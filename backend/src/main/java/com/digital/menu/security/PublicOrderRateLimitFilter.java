package com.digital.menu.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class PublicOrderRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(PublicOrderRateLimitFilter.class);
    private static final String INCR_WITH_TTL_SCRIPT =
        "local current = redis.call('INCR', KEYS[1]) "
            + "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
            + "return current";

    @Value("${app.ratelimit.public-orders.max-requests-per-window:60}")
    private int maxRequestsPerWindow;

    @Value("${app.ratelimit.public-orders.window-seconds:60}")
    private int windowSeconds;

    @Value("${app.ratelimit.redis-enabled:false}")
    private boolean redisEnabled;

    @Value("${app.ratelimit.redis-key-prefix:ratelimit:public-orders}")
    private String redisKeyPrefix;

    private final StringRedisTemplate redisTemplate;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final DefaultRedisScript<Long> incrWithTtl = new DefaultRedisScript<>(INCR_WITH_TTL_SCRIPT, Long.class);

    public PublicOrderRateLimitFilter(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !isPublicOrderPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = resolveClientAddress(request);
        int requestCount = incrementAndGetCount(key);
        if (requestCount > maxRequestsPerWindow) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(Math.max(1, windowSeconds)));
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Too many requests. Please try again shortly.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicOrderPath(String path) {
        if (path == null) return false;
        if ("/api/public/orders".equals(path)) return true;
        if (!path.startsWith("/api/public/")) return false;
        String suffix = "/orders";
        return path.endsWith(suffix) && path.length() > "/api/public".length() + suffix.length();
    }

    private String resolveClientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private int incrementAndGetCount(String clientKey) {
        if (redisEnabled && redisTemplate != null) {
            try {
                long windowBucket = Instant.now().getEpochSecond() / Math.max(1, windowSeconds);
                String redisKey = redisKeyPrefix + ":" + clientKey + ":" + windowBucket;
                Long count = redisTemplate.execute(incrWithTtl, java.util.List.of(redisKey), String.valueOf(windowSeconds));
                return count == null ? 1 : count.intValue();
            } catch (Exception ex) {
                log.warn("Redis rate limit unavailable; using in-memory fallback. reason={}", ex.getMessage());
            }
        }

        Instant now = Instant.now();
        Counter counter = counters.compute(clientKey, (k, current) -> {
            if (current == null || now.isAfter(current.windowStart.plusSeconds(Math.max(1, windowSeconds)))) {
                return new Counter(now, 1);
            }
            current.count += 1;
            return current;
        });
        if (counters.size() > 10_000) {
            counters.entrySet().removeIf(entry -> now.isAfter(entry.getValue().windowStart.plusSeconds(Math.max(1, windowSeconds))));
        }
        return counter.count;
    }

    private static final class Counter {
        private final Instant windowStart;
        private int count;

        private Counter(Instant windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
