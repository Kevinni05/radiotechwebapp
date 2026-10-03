package com.radiotech.radiotech_backend.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

public class TechnicianSkillRequest {

    @NotNull
    @Min(1)
    @Max(5)
    private Integer level;

    @NotBlank
    @Size(max = 200)
    private String certification;

    @NotNull
    private LocalDate expiration;

    @NotNull
    private Boolean authorized;

    @AssertTrue(message = "La certificazione scaduta non può essere autorizzata.")
    public boolean isAuthorizationConsistent() {
        if (expiration == null || authorized == null) {
            return true;
        }
        return !Boolean.TRUE.equals(authorized) || !expiration.isBefore(LocalDate.now());
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getCertification() {
        return certification;
    }

    public void setCertification(String certification) {
        this.certification = certification;
    }

    public LocalDate getExpiration() {
        return expiration;
    }

    public void setExpiration(LocalDate expiration) {
        this.expiration = expiration;
    }

    public Boolean getAuthorized() {
        return authorized;
    }

    public void setAuthorized(Boolean authorized) {
        this.authorized = authorized;
    }
}
