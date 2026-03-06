package com.digital.menu.dto;

public class RegisterAdminRequest {
    private String username;
    private String password;
    private String tenantId;
    private String role;
    private String backgroundVideoUrl;
    private String backgroundImageUrl;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getBackgroundVideoUrl() {
        return backgroundVideoUrl;
    }

    public void setBackgroundVideoUrl(String backgroundVideoUrl) {
        this.backgroundVideoUrl = backgroundVideoUrl;
    }

    public String getBackgroundImageUrl() {
        return backgroundImageUrl;
    }

    public void setBackgroundImageUrl(String backgroundImageUrl) {
        this.backgroundImageUrl = backgroundImageUrl;
    }
}
