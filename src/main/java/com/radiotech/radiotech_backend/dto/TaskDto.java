package com.radiotech.radiotech_backend.dto;

import com.radiotech.radiotech_backend.model.Task;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TaskDto {
    @NotBlank
    @Size(max = 160)
    private String title;

    @Size(max = 4000)
    private String description;

    private String operatorId;
    private String antennaId;
    private String tenantId;

    @Pattern(regexp = "(?i)BASSA|MEDIA|ALTA|CRITICA|LOW|MEDIUM|HIGH|CRITICAL", message = "Priorita non valida")
    private String priority;

    private String dueAt;

    public Task toModel() {
        Task task = new Task();
        task.setTitle(title.trim());
        task.setDescription(description == null ? null : description.trim());
        task.setOperatorId(blankToNull(operatorId));
        task.setAntennaId(blankToNull(antennaId));
        task.setTenantId(blankToNull(tenantId));
        task.setPriority(blankToNull(priority));
        task.setDueAt(blankToNull(dueAt));
        return task;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
