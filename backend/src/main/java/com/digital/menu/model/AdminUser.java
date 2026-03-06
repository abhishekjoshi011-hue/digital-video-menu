package com.digital.menu.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "admin_users")
public class AdminUser {
    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    private String passwordHash;
    private String tenantId;
    private String role = "ROLE_ADMIN";
    private String backgroundVideoUrl;
    private String backgroundImageUrl;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
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
