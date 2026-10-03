package com.radiotech.radiotech_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IncidentCreateRequest {
    @NotBlank(message = "Titolo incident obbligatorio.")
    @Size(max = 200, message = "Titolo incident troppo lungo.")
    private String title;

    @Size(max = 4000, message = "Descrizione incident troppo lunga.")
    private String description;

    @Pattern(regexp = "(?i)LOW|MEDIUM|HIGH|CRITICAL", message = "Severità incident non valida.")
    private String severity;

    @Size(max = 128, message = "Site ID troppo lungo.")
    private String siteId;

    @Size(max = 128, message = "Asset ID troppo lungo.")
    private String assetId;

    @Size(max = 128, message = "Task ID troppo lungo.")
    private String taskId;

    @Size(max = 128, message = "Operator ID troppo lungo.")
    private String assignedOperatorId;
}
