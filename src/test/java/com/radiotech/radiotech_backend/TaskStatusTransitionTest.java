package com.radiotech.radiotech_backend;

import com.radiotech.radiotech_backend.model.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskStatusTransitionTest {

    @Test
    void completedTaskRequiresChecklistBeforeClosure() {
        TaskStatus from = TaskStatus.IN_PROGRESS;
        TaskStatus to = TaskStatus.COMPLETED;

        assertTrue(from.canTransitionTo(to));
        assertFalse(TaskStatus.ASSIGNED.canTransitionTo(TaskStatus.CLOSED));
    }

    @Test
    void invalidTransitionsAreRejected() {
        assertFalse(TaskStatus.CANCELLED.canTransitionTo(TaskStatus.ASSIGNED));
        assertFalse(TaskStatus.COMPLETED.canTransitionTo(TaskStatus.IN_PROGRESS));
    }

    @Test
    void completionAndApprovalTransitionsRequireHigherPrivileges() {
        assertTrue(TaskStatus.IN_PROGRESS.requiresExecutionPermission());
        assertTrue(TaskStatus.COMPLETED.requiresCompletionPermission());
        assertTrue(TaskStatus.APPROVED.requiresApprovalPermission());
        assertFalse(TaskStatus.ASSIGNED.requiresCompletionPermission());
        assertFalse(TaskStatus.ASSIGNED.requiresApprovalPermission());
    }
}
