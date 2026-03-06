package com.digital.menu.service;

import com.digital.menu.dto.AuthRequest;
import com.digital.menu.dto.AuthResponse;
import com.digital.menu.dto.RegisterAdminRequest;
import com.digital.menu.model.AdminUser;
import com.digital.menu.repository.AdminUserRepository;
import com.digital.menu.security.AdminUserPrincipal;
import com.digital.menu.security.JwtService;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final Set<String> ALLOWED_ROLES = Set.of("ROLE_OWNER", "ROLE_MANAGER", "ROLE_ADMIN", "ROLE_CAPTAIN", "ROLE_KITCHEN");
    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Value("${app.auth.allow-public-registration:false}")
    private boolean allowPublicRegistration;

    @Value("${app.auth.setup-key:}")
    private String setupKey;

    public AuthService(
        AdminUserRepository adminUserRepository,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager,
        JwtService jwtService
    ) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterAdminRequest request, String providedSetupKey) {
        if (!allowPublicRegistration) {
            if (setupKey == null || setupKey.isBlank()) {
                throw new IllegalStateException("Admin registration is disabled");
            }
            if (providedSetupKey == null || !setupKey.equals(providedSetupKey)) {
                throw new IllegalArgumentException("Invalid setup key");
            }
        }

        if (request.getTenantId() == null || request.getTenantId().isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new IllegalArgumentException("password must be at least 8 characters");
        }
        if (adminUserRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("username already exists");
        }

        AdminUser user = new AdminUser();
        user.setUsername(request.getUsername());
        user.setTenantId(request.getTenantId());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setBackgroundVideoUrl(trimToEmpty(request.getBackgroundVideoUrl()));
        user.setBackgroundImageUrl(trimToEmpty(request.getBackgroundImageUrl()));
        String role = normalizeRole(request.getRole());
        user.setRole(role);
        adminUserRepository.save(user);

        AdminUserPrincipal principal = new AdminUserPrincipal(user);
        String token = jwtService.generateToken(principal);
        return new AuthResponse(token, user.getTenantId(), user.getUsername());
    }

    public AuthResponse login(AuthRequest request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        AdminUser user = adminUserRepository
            .findByUsername(request.getUsername())
            .orElseThrow(() -> new IllegalArgumentException("invalid credentials"));

        AdminUserPrincipal principal = new AdminUserPrincipal(user);
        String token = jwtService.generateToken(principal);
        return new AuthResponse(token, user.getTenantId(), user.getUsername());
    }

    private String normalizeRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return "ROLE_ADMIN";
        }
        String role = rawRole.toUpperCase().startsWith("ROLE_") ? rawRole.toUpperCase() : "ROLE_" + rawRole.toUpperCase();
        if (!ALLOWED_ROLES.contains(role)) {
            throw new IllegalArgumentException("Invalid role");
        }
        return role;
    }

    private String trimToEmpty(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed;
    }
}
