package com.radiotech.radiotech_backend.dto;

import com.radiotech.radiotech_backend.model.Antenna;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AntennaDtoTest {

    @Test
    void mapsAssetIdentityAndHierarchyMetadata() {
        AntennaDto dto = new AntennaDto();
        dto.setName("RRU settore nord");
        dto.setLat(41.1);
        dto.setLng(16.8);
        dto.setStatus("ATTIVA");
        dto.setAssetType("rru");
        dto.setParentAssetId("antenna-1");
        dto.setSerialNumber("SN-001");
        dto.setManufacturer("Vendor");
        dto.setModel("R-500");
        dto.setInstallationDate("2026-10-01");

        Antenna antenna = dto.toModel();

        assertEquals("RRU", antenna.getAssetType());
        assertEquals("antenna-1", antenna.getParentAssetId());
        assertEquals("SN-001", antenna.getSerialNumber());
        assertEquals("Vendor", antenna.getManufacturer());
        assertEquals("R-500", antenna.getModel());
        assertEquals("2026-10-01", antenna.getInstallationDate());
    }

    @Test
    void defaultsLegacyCreatePayloadToAntennaAssetType() {
        AntennaDto dto = new AntennaDto();
        dto.setName("Legacy antenna");
        dto.setLat(41.1);
        dto.setLng(16.8);
        dto.setStatus("ATTIVA");

        Antenna antenna = dto.toModel();

        assertEquals("ANTENNA", antenna.getAssetType());
        assertNull(antenna.getParentAssetId());
    }
}
