package com.radiotech.radiotech_backend.model;

import java.time.LocalDate;

public record TechnicianSkill(
        String operatorId,
        TechnicianSkillType skill,
        int level,
        String certification,
        LocalDate expiration,
        boolean authorized,
        String updatedAt) {
}
