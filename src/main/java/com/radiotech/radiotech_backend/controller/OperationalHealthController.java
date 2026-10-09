package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.security.*;
import com.radiotech.radiotech_backend.service.OperationalHealthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationalHealthController {
    private final OperationalHealthService service;
    public OperationalHealthController(OperationalHealthService service) { this.service = service; }
    @GetMapping("/health")
    public Object health() throws Exception {
        if (!TenantAccessPolicy.canAccess(SecurityContextAccessor.currentRole(), Permission.ANALYTICS_READ)) throw new SecurityException("Monitoraggio riservato ai responsabili.");
        return service.snapshot();
    }
}
