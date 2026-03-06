package com.digital.menu.dto;

public class TenantBrandingResponse {
    private final String tenantId;
    private final String backgroundVideoUrl;
    private final String backgroundImageUrl;

    public TenantBrandingResponse(String tenantId, String backgroundVideoUrl, String backgroundImageUrl) {
        this.tenantId = tenantId;
        this.backgroundVideoUrl = backgroundVideoUrl;
        this.backgroundImageUrl = backgroundImageUrl;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getBackgroundVideoUrl() {
        return backgroundVideoUrl;
    }

    public String getBackgroundImageUrl() {
        return backgroundImageUrl;
    }
}
