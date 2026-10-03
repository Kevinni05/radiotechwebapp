package com.radiotech.radiotech_backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TechnicianSkillType {
    RF,
    LTE,
    FIVE_G("5G"),
    FIBER,
    IP,
    MICROWAVE,
    POWER,
    HVAC,
    SAFETY;

    private final String code;

    TechnicianSkillType() {
        this.code = name();
    }

    TechnicianSkillType(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static TechnicianSkillType fromCode(String code) {
        for (TechnicianSkillType type : values()) {
            if (type.code.equals(code) || type.name().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Competenza non supportata.");
    }
}
