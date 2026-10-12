package com.radiotech.radiotech_backend.dto;

import com.radiotech.radiotech_backend.model.MaintenanceReport;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class MaintenanceReportDto {
    private String taskId;
    private String antennaId;

    @Size(max = 4000)
    private String description;
    @Size(max = 4000)
    private String workPerformed;
    @Size(max = 4000)
    private String findings;
    @Size(max = 4000)
    private String operatorNotes;
    private Map<String, Boolean> checklist;
    private String digitalSignature;

    private String startedAt;
    private String completedAt;
    private Map<String, Double> measurements;
    @Size(max = 100)
    private Map<@Size(max = 150) String, @Size(max = 24) String> measurementUnits;
    @jakarta.validation.constraints.Pattern(regexp = "sha256:[a-f0-9]{64}")
    private String checklistVersion;
    private List<Map<String, Object>> materialsUsed = new ArrayList<>();
    private List<@Size(max = 2048) String> attachments = new ArrayList<>();

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;

    @AssertTrue(message = "Il report deve riferirsi a un task o a un'antenna")
    public boolean hasTarget() {
        return notBlank(taskId) || notBlank(antennaId);
    }

    @AssertTrue(message = "Le quantità dei materiali devono essere numeri interi positivi")
    public boolean hasValidMaterials() {
        if (materialsUsed == null)
            return true;
        return materialsUsed.stream().allMatch(material -> {
            if (material == null || material.get("quantity") == null)
                return false;
            Object value = material.get("quantity");
            return value instanceof Number quantity && Double.isFinite(quantity.doubleValue())
                    && quantity.doubleValue() > 0 && quantity.doubleValue() == quantity.intValue();
        });
    }

    public MaintenanceReport toModel() {
        MaintenanceReport report = new MaintenanceReport();
        report.setTaskId(blankToNull(taskId));
        report.setAntennaId(blankToNull(antennaId));
        report.setDescription(description);
        report.setWorkPerformed(workPerformed);
        report.setFindings(findings);
        report.setOperatorNotes(operatorNotes);
        report.setChecklist(checklist);
        report.setDigitalSignature(digitalSignature);
        report.setStartedAt(startedAt);
        report.setCompletedAt(completedAt);
        report.setMeasurements(measurements);
        report.setMeasurementUnits(measurementUnits);
        report.setChecklistVersion(checklistVersion);
        report.setMaterialsUsed(materialsUsed == null ? new ArrayList<>() : materialsUsed);
        report.setAttachments(attachments == null ? new ArrayList<>() : attachments);
        report.setLatitude(latitude);
        report.setLongitude(longitude);
        return report;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return notBlank(value) ? value.trim() : null;
    }
}
