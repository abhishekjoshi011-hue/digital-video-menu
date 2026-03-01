package com.digital.menu.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class PublicOrderRateLimitFilterTest {
    private PublicOrderRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        ObjectProvider<StringRedisTemplate> provider = beanFactory.getBeanProvider(StringRedisTemplate.class);
        filter = new PublicOrderRateLimitFilter(provider);
        ReflectionTestUtils.setField(filter, "redisEnabled", false);
        ReflectionTestUtils.setField(filter, "maxRequestsPerWindow", 2);
        ReflectionTestUtils.setField(filter, "windowSeconds", 60);
    }

    @Test
    void shouldRateLimitDirectPublicOrdersPath() throws Exception {
        assertEquals(200, execute("POST", "/api/public/orders").getStatus());
        assertEquals(200, execute("POST", "/api/public/orders").getStatus());

        MockHttpServletResponse third = execute("POST", "/api/public/orders");
        assertEquals(429, third.getStatus());
        assertTrue(third.getContentAsString().contains("Too many requests"));
    }

    @Test
    void shouldAlsoRateLimitTenantScopedPublicOrdersPath() throws Exception {
        assertEquals(200, execute("POST", "/api/public/demo-tenant/orders").getStatus());
        assertEquals(200, execute("POST", "/api/public/demo-tenant/orders").getStatus());

        MockHttpServletResponse third = execute("POST", "/api/public/demo-tenant/orders");
        assertEquals(429, third.getStatus());
    }

    private MockHttpServletResponse execute(String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("10.10.10.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
