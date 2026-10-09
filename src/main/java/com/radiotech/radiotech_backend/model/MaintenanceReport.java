package com.radiotech.radiotech_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@com.google.cloud.firestore.annotation.IgnoreExtraProperties
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceReport {
    private String id;
    private String tenantId;
    private String taskId;
    private String taskTitle;
    private String antennaName;

    /** Firestore document id of the operator that submitted the report. */
    private String operatorId;

    /** Firebase UID of the operator that submitted the report. */
    private String operatorFirebaseUid;

    private String operatorName;

    private String antennaId;
    private String status; // SUBMITTED, APPROVAL_PENDING, APPROVED, REJECTED
    private String startedAt;
    private String completedAt;
    private String submittedAt;
    @JsonIgnore
    private Long archiveAt;
    @JsonIgnore
    private List<String> operatorRefs;
    /** Logical removal only: signed content and review state remain intact. */
    private String removedAt;
    private String removedBy;
    private String reviewedAt;
    private String reviewedBy;
    private String reviewNote;
    @JsonIgnore
    private String inventoryConsumptionStatus;
    @JsonIgnore
    private String approvalAttemptId;
    @JsonIgnore
    private String approvalLeaseUntil;
    private String description;
    private String workPerformed;
    private String findings;
    private Map<String, Double> measurements;
    private List<Map<String, Object>> materialsUsed = new ArrayList<>();
    private List<String> attachments = new ArrayList<>();
    private Double latitude;
    private Double longitude;
    private String operatorNotes;
    private Map<String, Boolean> checklist;
    private String digitalSignature;
    @JsonIgnore
    private String integrityHash;
}
