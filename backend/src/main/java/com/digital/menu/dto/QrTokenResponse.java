package com.digital.menu.dto;

import java.time.Instant;

public class QrTokenResponse {
    private String token;
    private String menuUrl;
    private String tenantId;
    private Integer tableNumber;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public QrTokenResponse(
        String token,
        String menuUrl,
        String tenantId,
        Integer tableNumber,
        boolean active,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.token = token;
        this.menuUrl = menuUrl;
        this.tenantId = tenantId;
        this.tableNumber = tableNumber;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getToken() {
        return token;
    }

    public String getMenuUrl() {
        return menuUrl;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Integer getTableNumber() {
        return tableNumber;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
