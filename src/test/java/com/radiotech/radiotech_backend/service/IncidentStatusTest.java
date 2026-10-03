package com.radiotech.radiotech_backend.service;

import com.radiotech.radiotech_backend.model.IncidentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentStatusTest {

    @Test
    void lifecycleAllowsOnlyTheNextOperationalTransition() {
        assertTrue(IncidentStatus.DETECTED.canTransitionTo(IncidentStatus.ACKNOWLEDGED));
        assertTrue(IncidentStatus.MITIGATED.canTransitionTo(IncidentStatus.RESOLVED));
        assertTrue(IncidentStatus.RESOLVED.canTransitionTo(IncidentStatus.POST_MORTEM));
        assertFalse(IncidentStatus.DETECTED.canTransitionTo(IncidentStatus.RESOLVED));
        assertFalse(IncidentStatus.POST_MORTEM.canTransitionTo(IncidentStatus.DETECTED));
    }
}