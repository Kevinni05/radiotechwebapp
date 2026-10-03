package com.radiotech.radiotech_backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Antenna {

    private String id;

    private String tenantId;

    /** Human readable station name, shown in the Control Room and on mobile. */
    private String name;

    /**
     * Short code printed on the physical QR plate of the station. Operators scan
     * it with the mobile app and the backend resolves it to this document.
     */
    private String code;

    /**
     * SITE, TOWER, SECTOR, ANTENNA, RRU, BBU, ROUTER, SWITCH, UPS, BATTERY,
     * GENERATOR, FIBER or MICROWAVE.
     */
    private String assetType;

    private String parentAssetId;

    private String serialNumber;

    private String manufacturer;

    private String model;

    private String installationDate;

    private Double lat;

    private Double lng;

    /** ATTIVA | MANUTENZIONE | OFFLINE | CRITICA */
    private String status;

    /** Free text address or site description. */
    private String site;

    private AntennaSpecs specs;

    private String createdAt;

    private String updatedAt;
}
