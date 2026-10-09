package com.radiotech.radiotech_backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@com.google.cloud.firestore.annotation.IgnoreExtraProperties
public class Task {

    private String id;

    private String tenantId;

    private String title;

    private String description;

    private String operatorId;

    /** Legacy Firestore field kept for backward compatibility during migration. */
    private String operator_uid;

    private String operatorName;

    private String operatorFirebaseUid;

    private String antennaId;

    private String status;

    private String priority;

    private String dueAt;

    private String createdAt;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private Long archiveAt;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.util.List<String> operatorRefs;

    private String updatedAt;

    private String completedAt;

    private Double checkInLatitude;

    private Double checkInLongitude;

    private String checkedInAt;

    private String checkedInBy;

    private Double checkOutLatitude;

    private Double checkOutLongitude;

    private String checkedOutAt;

    private String checkedOutBy;

    private String createdBy;

    private String updatedBy;

    private java.util.Map<String, Object> metadata;
}
