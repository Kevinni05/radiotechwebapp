package com.radiotech.radiotech_backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Operator {

    private String id;

    private String tenantId;

    private String fullName;

    private String email;

    private String phone;

    private String birthDate;

    private String company;

    private String specialization;

    private String level;

    private String shift;

    private String status;

    private String role;

    private String firebaseUid;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String qrCodeToken;

    private String qrExpiresAt;

    private String qrValidityMode;

    private String qrFixedExpiresAt;

    /** Timestamp of the first successful QR login with this badge. */
    private String qrUsedAt;

    /** Timestamp of the most recent successful QR login with this badge. */
    private String qrLastUsedAt;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private List<String> fcmTokens = new ArrayList<>();

    private String lastSeen;

    private String createdAt;

    private String updatedAt;
}
