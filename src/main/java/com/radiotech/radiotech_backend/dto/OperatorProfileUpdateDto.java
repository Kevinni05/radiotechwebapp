package com.radiotech.radiotech_backend.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OperatorProfileUpdateDto {
    @Size(max = 120)
    private String fullName;

    @Size(max = 40)
    private String phone;

    @Size(max = 20)
    private String birthDate;

    @Size(max = 120)
    private String company;

    @Size(max = 120)
    private String specialization;

    @Pattern(regexp = "(?i)JUNIOR|TECNICO|SENIOR", message = "Livello operatore non valido")
    private String level;

    @Size(max = 40)
    private String shift;
}
