package com.radiotech.radiotech_backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Incident {
    private String id;
    private String tenantId;
    private String title;
    private String description;
    private String severity;
    private String status;
    private String siteId;
    private String assetId;
    private String taskId;
    private String assignedOperatorId;
    private String detectedAt;
    private String acknowledgedAt;
    private String assignedAt;
    private String investigatingAt;
    private String mitigatedAt;
    private String resolvedAt;
    private String postMortemAt;
    private String createdAt;
    private String updatedAt;
    private String createdBy;
    private String updatedBy;
    private String rootCause;
    private String resolution;
    private java.util.List<java.util.Map<String, Object>> timeline = new java.util.ArrayList<>();
    private String closedAt;
}
