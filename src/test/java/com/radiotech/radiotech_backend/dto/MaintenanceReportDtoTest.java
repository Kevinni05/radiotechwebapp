package com.radiotech.radiotech_backend.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MaintenanceReportDtoTest {
    @Test void materialQuantitiesMustBeWholePositiveInventoryUnits() {
        var request = new MaintenanceReportDto();
        for (Number quantity : List.<Number>of(1, 2L, 3.0, Integer.MAX_VALUE)) {
            request.setMaterialsUsed(List.of(Map.of("inventoryId", "cable", "quantity", quantity)));
            assertTrue(request.hasValidMaterials(), quantity.toString());
        }
        for (Object quantity : List.of(0, -1, 1.5, Double.NaN, Double.POSITIVE_INFINITY,
                (long) Integer.MAX_VALUE + 1, 4294967297L, "1")) {
            request.setMaterialsUsed(List.of(Map.of("inventoryId", "cable", "quantity", quantity)));
            assertFalse(request.hasValidMaterials(), quantity.toString());
        }
        request.setMaterialsUsed(List.of(Map.of("inventoryId", "cable")));
        assertFalse(request.hasValidMaterials());
        request.setMaterialsUsed(List.of());
        assertTrue(request.hasValidMaterials());
    }
}
