package com.radiotech.radiotech_backend.service;

import com.radiotech.radiotech_backend.model.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskLifecycleTest {

    @Test
    void legalTransitionsAreAllowedAndIllegalJumpsAreRejected() {
        assertTrue(TaskStatus.ASSIGNED.canTransitionTo(TaskStatus.ACCEPTED));
        assertTrue(TaskStatus.EN_ROUTE.canTransitionTo(TaskStatus.CHECKED_IN));
        assertFalse(TaskStatus.ASSIGNED.canTransitionTo(TaskStatus.CHECKED_IN));
        assertFalse(TaskStatus.CHECKED_IN.canTransitionTo(TaskStatus.CLOSED));
    }

    @Test
    void idempotencyKeyIsStableAndScopedByTenantAndActor() {
        String first = TaskService.idempotencyDocumentId("tenant-a", "operator-1", "ACCEPTED");
        String second = TaskService.idempotencyDocumentId("tenant-a", "operator-1", "ACCEPTED");
        String differentTenant = TaskService.idempotencyDocumentId("tenant-b", "operator-1", "ACCEPTED");

        assertEquals(first, second);
        assertFalse(first.equals(differentTenant));
    }
}
