package com.radiotech.radiotech_backend.model;

public class CapoProfile {

    private String uid;
    private String tenantId;
    private String fullName;
    private String email;
    private String phone;
    private String company;
    private String role;
    private String status;
    private String photoUrl;
    private String lastSeen;
    private String createdAt;
    private String updatedAt;

    public CapoProfile() {
    }

    public CapoProfile(
            String uid,
            String fullName,
            String email,
            String phone,
            String company,
            String role,
            String status,
            String photoUrl,
            String lastSeen,
            String createdAt,
            String updatedAt) {

        this.uid = uid;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.company = company;
        this.role = role;
        this.status = status;
        this.photoUrl = photoUrl;
        this.lastSeen = lastSeen;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getUid() {
        return uid;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPhone() {
        return phone;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getCompany() {
        return company;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getRole() {
        return role;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setLastSeen(String lastSeen) {
        this.lastSeen = lastSeen;
    }

    public String getLastSeen() {
        return lastSeen;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}