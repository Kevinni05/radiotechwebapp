package com.radiotech.radiotech_backend.dto;
import jakarta.validation.constraints.*;
public final class WorkforceRequests {
    private WorkforceRequests() {}
    public record Shift(@NotBlank @Pattern(regexp = "START|BREAK|RESUME|END") String action,
            @NotBlank @Pattern(regexp = "READY|NEEDS_BREAK|REQUEST_SUPPORT") String readiness,
            @NotNull @Min(0) Long expectedVersion,
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9-]{16,64}") String operationId) {}
    public record Signal(@NotBlank @Pattern(regexp = "NEAR_MISS|HAZARD|SUPPORT_REQUEST") String type,
            @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String severity,
            @NotBlank @Size(max = 1000) String description,
            @Size(max = 128) @Pattern(regexp = "[^/]*") String assetId,
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9-]{16,64}") String operationId) {}
    public record Review(@NotBlank @Pattern(regexp = "ACKNOWLEDGED|RESOLVED") String status,
            @Size(max = 1000) String resolution, @NotNull @Min(0) Long expectedVersion) {}
}
