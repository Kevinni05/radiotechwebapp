package com.radiotech.radiotech_backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Intervention {

    private String id;
    private String antennaId;
    private String operatorId;
    private String operatorName;
    private String type;
    private String description;
    private String status;
    private String createdAt;
    private String completedAt;
    private String reportUrl;
}