package com.radiotech.radiotech_backend;

import com.radiotech.radiotech_backend.controller.OperatorMobileController;
import com.radiotech.radiotech_backend.dto.MaintenanceReportDto;
import com.radiotech.radiotech_backend.model.MaintenanceReport;
import com.radiotech.radiotech_backend.model.Operator;
import com.radiotech.radiotech_backend.model.Task;
import com.radiotech.radiotech_backend.security.TenantInvitationService;
import com.radiotech.radiotech_backend.service.MaintenanceReportService;
import com.radiotech.radiotech_backend.service.OperatorService;
import com.radiotech.radiotech_backend.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperatorMobileControllerWorkflowTest {

        @Test
        void acceptingAssignedTaskTransitionsToAccepted() throws Exception {
                TaskService taskService = mock(TaskService.class);
                OperatorService operatorService = mock(OperatorService.class);
                OperatorMobileController controller = new OperatorMobileController(
                                taskService,
                                mock(MaintenanceReportService.class),
                                operatorService,
                                mock(TenantInvitationService.class));

                Operator operator = new Operator();
                operator.setId("operator-doc");
                operator.setTenantId("tenant-a");
                operator.setFirebaseUid("operator-uid");
                Task task = new Task();
                task.setId("task-a");
                task.setTenantId("tenant-a");
                task.setOperatorId("operator-doc");
                task.setOperatorFirebaseUid("operator-uid");
                when(operatorService.getByFirebaseUid("operator-uid")).thenReturn(operator);
                when(taskService.getById("task-a")).thenReturn(task);
                when(taskService.updateStatus("task-a", "ACCEPTED")).thenReturn(task);

                assertEquals(HttpStatus.OK, controller.accept("operator-uid", "task-a").getStatusCode());
                verify(taskService).updateStatus("task-a", "ACCEPTED");
        }

        @Test
        void reportEndpointForwardsIdempotencyKey() throws Exception {
                MaintenanceReportService reportService = mock(MaintenanceReportService.class);
                OperatorMobileController controller = new OperatorMobileController(
                                mock(TaskService.class), reportService, mock(OperatorService.class),
                                mock(TenantInvitationService.class));
                when(reportService.submit(any(MaintenanceReport.class), eq("operator-uid"), eq("request-123")))
                                .thenReturn(new MaintenanceReport());
                when(reportService.verificationUrl(any(MaintenanceReport.class)))
                                .thenReturn("https://reports.example.test/verify/token");

                assertEquals(HttpStatus.CREATED,
                                controller.standaloneReport("operator-uid", new MaintenanceReportDto(), "request-123")
                                                .getStatusCode());
                verify(reportService).submit(any(MaintenanceReport.class), eq("operator-uid"), eq("request-123"));
        }

        @Test
        void reportEndpointReturnsConflictWhenIdempotencyKeyIsReused() throws Exception {
                MaintenanceReportService reportService = mock(MaintenanceReportService.class);
                OperatorMobileController controller = new OperatorMobileController(
                                mock(TaskService.class), reportService, mock(OperatorService.class),
                                mock(TenantInvitationService.class));
                when(reportService.submit(any(MaintenanceReport.class), eq("operator-uid"), eq("request-123")))
                                .thenThrow(new MaintenanceReportService.IdempotencyConflictException());

                assertEquals(HttpStatus.CONFLICT,
                                controller.standaloneReport("operator-uid", new MaintenanceReportDto(), "request-123")
                                                .getStatusCode());
        }

        @Test
        void completingTaskRequiresGpsCheckOutAndUsesGeofencedService() throws Exception {
                TaskService taskService = mock(TaskService.class);
                OperatorService operatorService = mock(OperatorService.class);
                OperatorMobileController controller = new OperatorMobileController(
                                taskService, mock(MaintenanceReportService.class), operatorService,
                                mock(TenantInvitationService.class));

                assertEquals(HttpStatus.BAD_REQUEST, controller.complete("operator-uid", "task-a", null)
                                .getStatusCode());

                Operator operator = new Operator();
                operator.setId("operator-doc");
                Task task = new Task();
                task.setOperatorId("operator-doc");
                when(operatorService.getByFirebaseUid("operator-uid")).thenReturn(operator);
                when(taskService.getById("task-a")).thenReturn(task);
                when(taskService.completeTaskWithLocation("task-a", "operator-uid", "operator-doc", 41.1, 16.8))
                                .thenReturn(task);

                assertEquals(HttpStatus.OK, controller.complete("operator-uid", "task-a",
                                java.util.Map.of("latitude", 41.1, "longitude", 16.8, "accuracy", 15.0))
                                .getStatusCode());
                verify(taskService).completeTaskWithLocation("task-a", "operator-uid", "operator-doc", 41.1, 16.8);
        }
}
