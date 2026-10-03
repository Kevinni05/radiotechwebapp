package com.radiotech.radiotech_backend.dto;

import com.radiotech.radiotech_backend.model.Antenna;
import com.radiotech.radiotech_backend.model.AntennaSpecs;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AntennaDto {
    @Size(max = 80)
    private String code;

    @Size(max = 24)
    private String assetType;

    @Size(max = 128)
    private String parentAssetId;

    @Size(max = 128)
    private String serialNumber;

    @Size(max = 120)
    private String manufacturer;

    @Size(max = 120)
    private String model;

    @Size(max = 40)
    private String installationDate;

    @NotBlank
    @Size(max = 120)
    private String name;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double lat;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double lng;

    @NotBlank
    private String status;

    @Valid
    private SpecsDto specs;

    public Antenna toModel() {
        Antenna antenna = new Antenna();
        antenna.setCode(code == null ? null : code.trim());
        antenna.setAssetType(assetType == null || assetType.isBlank() ? "ANTENNA" : assetType.trim().toUpperCase());
        antenna.setParentAssetId(blankToNull(parentAssetId));
        antenna.setSerialNumber(blankToNull(serialNumber));
        antenna.setManufacturer(blankToNull(manufacturer));
        antenna.setModel(blankToNull(model));
        antenna.setInstallationDate(blankToNull(installationDate));
        antenna.setName(name.trim());
        antenna.setLat(lat);
        antenna.setLng(lng);
        antenna.setStatus(status.trim().toUpperCase());
        if (specs != null) {
            antenna.setSpecs(new AntennaSpecs(specs.frequencyMHz, specs.powerWatts, specs.ros, specs.temperature));
        }
        return antenna;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Data
    public static class SpecsDto {
        @DecimalMin(value = "0.001", inclusive = true)
        @DecimalMax(value = "100000.0", inclusive = true)
        private Double frequencyMHz;

        @DecimalMin("0.0")
        @DecimalMax("100000.0")
        private Double powerWatts;

        @DecimalMin("0.0")
        @DecimalMax("100.0")
        private Double ros;

        @DecimalMin("-100.0")
        @DecimalMax("200.0")
        private Double temperature;
    }
}
