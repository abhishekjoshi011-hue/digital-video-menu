package com.digital.menu.controllers;

import com.digital.menu.model.Dish;
import com.digital.menu.model.AdminUser;
import com.digital.menu.dto.TenantBrandingResponse;
import com.digital.menu.repository.AdminUserRepository;
import com.digital.menu.service.DishService;
import com.digital.menu.service.TableQrServicePort;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicMenuController {
    private final DishService dishService;
    private final TableQrServicePort tableQrService;
    private final AdminUserRepository adminUserRepository;

    public PublicMenuController(
        DishService dishService,
        TableQrServicePort tableQrService,
        AdminUserRepository adminUserRepository
    ) {
        this.dishService = dishService;
        this.tableQrService = tableQrService;
        this.adminUserRepository = adminUserRepository;
    }

    @GetMapping("/menu")
    public List<Dish> getMenuForQrToken(@RequestParam("t") String token) {
        var context = tableQrService.validatePublicQrToken(token);
        return dishService.getAllDishes(context.tenantId());
    }

    @GetMapping("/{tenantId}/menu")
    public List<Dish> getTenantMenu(@PathVariable String tenantId) {
        return dishService.getAllDishes(tenantId);
    }

    @GetMapping("/branding")
    public TenantBrandingResponse getBrandingForQrToken(@RequestParam("t") String token) {
        var context = tableQrService.validatePublicQrToken(token);
        return getBrandingForTenant(context.tenantId());
    }

    @GetMapping("/{tenantId}/branding")
    public TenantBrandingResponse getBrandingForTenantId(@PathVariable String tenantId) {
        return getBrandingForTenant(tenantId);
    }

    private TenantBrandingResponse getBrandingForTenant(String tenantId) {
        List<AdminUser> admins = adminUserRepository.findByTenantId(tenantId);
        AdminUser preferred = admins.stream()
            .filter(admin -> notBlank(admin.getBackgroundVideoUrl()) || notBlank(admin.getBackgroundImageUrl()))
            .findFirst()
            .orElse(admins.isEmpty() ? null : admins.get(0));

        String video = preferred == null ? null : trimToNull(preferred.getBackgroundVideoUrl());
        String image = preferred == null ? null : trimToNull(preferred.getBackgroundImageUrl());
        return new TenantBrandingResponse(tenantId, video, image);
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
