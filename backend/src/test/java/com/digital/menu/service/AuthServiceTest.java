package com.digital.menu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.digital.menu.dto.AuthRequest;
import com.digital.menu.dto.AuthResponse;
import com.digital.menu.dto.RegisterAdminRequest;
import com.digital.menu.model.AdminUser;
import com.digital.menu.repository.AdminUserRepository;
import com.digital.menu.security.AdminUserPrincipal;
import com.digital.menu.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AdminUserRepository adminUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(adminUserRepository, passwordEncoder, authenticationManager, jwtService);
        ReflectionTestUtils.setField(authService, "allowPublicRegistration", false);
        ReflectionTestUtils.setField(authService, "setupKey", "setup-123");
    }

    @Test
    void register_rejectsWhenSetupKeyInvalid() {
        RegisterAdminRequest req = request("tenant-a", "admin", "password123", "ADMIN");
        assertThrows(IllegalArgumentException.class, () -> authService.register(req, "wrong-key"));
    }

    @Test
    void register_savesUserAndReturnsToken() {
        RegisterAdminRequest req = request("tenant-a", "admin", "password123", "MANAGER");
        when(adminUserRepository.existsByUsername("admin")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(jwtService.generateToken(any(AdminUserPrincipal.class))).thenReturn("jwt-token");

        AuthResponse response = authService.register(req, "setup-123");

        assertEquals("jwt-token", response.getToken());
        assertEquals("tenant-a", response.getTenantId());
        assertEquals("admin", response.getUsername());

        ArgumentCaptor<AdminUser> captor = ArgumentCaptor.forClass(AdminUser.class);
        verify(adminUserRepository).save(captor.capture());
        assertEquals("ROLE_MANAGER", captor.getValue().getRole());
        assertEquals("encoded", captor.getValue().getPasswordHash());
    }

    @Test
    void login_returnsTokenForExistingUser() {
        AuthRequest request = new AuthRequest();
        request.setUsername("owner");
        request.setPassword("secret123");

        AdminUser user = new AdminUser();
        user.setUsername("owner");
        user.setTenantId("tenant-a");
        user.setRole("ROLE_OWNER");

        when(adminUserRepository.findByUsername("owner")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(AdminUserPrincipal.class))).thenReturn("jwt-owner");

        AuthResponse response = authService.login(request);

        assertEquals("jwt-owner", response.getToken());
        assertEquals("tenant-a", response.getTenantId());
        assertEquals("owner", response.getUsername());
    }

    private RegisterAdminRequest request(String tenantId, String username, String password, String role) {
        RegisterAdminRequest req = new RegisterAdminRequest();
        req.setTenantId(tenantId);
        req.setUsername(username);
        req.setPassword(password);
        req.setRole(role);
        return req;
    }
}

