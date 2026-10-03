package com.radiotech.radiotech_backend;

import com.radiotech.radiotech_backend.security.GeoFencePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoFencePolicyTest {

    @Test
    void acceptsLocationsInsideConfiguredRadius() {
        assertTrue(GeoFencePolicy.isWithinRadius(41.1171, 16.8719, 41.1175, 16.8721, 250));
    }

    @Test
    void rejectsLocationsOutsideConfiguredRadius() {
        assertFalse(GeoFencePolicy.isWithinRadius(41.1171, 16.8719, 41.1271, 16.8719, 250));
    }

    @Test
    void rejectsInvalidCoordinatesAndRadii() {
        assertThrows(IllegalArgumentException.class,
                () -> GeoFencePolicy.distanceMeters(91, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> GeoFencePolicy.isWithinRadius(0, 0, 0, 0, 0));
    }

    @Test
    void gpsAccuracyMustBeFiniteAndWithinOperationalLimit() {
        GeoFencePolicy.validateAccuracy(25);
        GeoFencePolicy.validateAccuracy(100);
        assertThrows(IllegalArgumentException.class, () -> GeoFencePolicy.validateAccuracy(100.1));
        assertThrows(IllegalArgumentException.class, () -> GeoFencePolicy.validateAccuracy(Double.NaN));
    }
}