package com.radiotech.radiotech_backend.service;

import com.radiotech.radiotech_backend.model.Task;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskServiceCheckoutTest {

    @Test
    void nullTaskStatusIsNotAnIdempotentCheckOut() {
        Task task = new Task();
        task.setCheckedOutBy("operator-a");

        assertFalse(TaskService.isCheckoutAlreadyCompleted(task, "operator-a"));
    }

    @Test
    void completedCheckOutIsIdempotentOnlyForTheSameOperator() {
        Task task = new Task();
        task.setStatus("COMPLETED");
        task.setCheckedOutBy("operator-a");

        assertTrue(TaskService.isCheckoutAlreadyCompleted(task, "operator-a"));
        assertFalse(TaskService.isCheckoutAlreadyCompleted(task, "operator-b"));
    }
}