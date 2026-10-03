package com.radiotech.radiotech_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class IncidentTransitionRequest {
    @NotBlank(message = "Stato incident obbligatorio.")
    @Pattern(regexp = "(?i)DETECTED|ACKNOWLEDGED|ASSIGNED|INVESTIGATING|MITIGATED|RESOLVED|POST_MORTEM|CLOSED",
            message = "Stato incident non valido.")
    private String status;

    @jakarta.validation.constraints.Size(max = 4000)
    private String rootCause;

    @jakarta.validation.constraints.Size(max = 4000)
    private String resolution;
}
