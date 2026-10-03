package com.radiotech.radiotech_backend;

import com.radiotech.radiotech_backend.controller.TelecomToolsController;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TelecomToolsControllerTest {
    private final TelecomToolsController controller = new TelecomToolsController();

    @Test
    void fsplUsesStandardFormula() {
        var response = controller.fspl(Map.of("frequencyMHz", 2400d, "distanceKm", 1d));
        assertEquals(100.0442, (Double) ((Map<?, ?>) response.getBody()).get("fsplDb"), 0.01);
    }

    @Test
    void eirpAddsGainAndSubtractsLoss() {
        var response = controller.eirp(Map.of("txPowerDbm", 20d, "antennaGainDbi", 10d, "cableLossDb", 2d));
        assertEquals(28d, (Double) ((Map<?, ?>) response.getBody()).get("eirpDbm"), 0.0001);
    }

    @Test
    void invalidFsplIsRejected() {
        assertEquals(400, controller.fspl(Map.of("frequencyMHz", 0d, "distanceKm", 1d)).getStatusCode().value());
    }
}
