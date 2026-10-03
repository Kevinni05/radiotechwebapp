package com.radiotech.radiotech_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkFirebaseRequest {

    private String operatorId;

    private String firebaseUid;
}