package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.dto.TechnicianSkillRequest;
import com.radiotech.radiotech_backend.model.TechnicianSkillType;
import com.radiotech.radiotech_backend.security.Permission;
import com.radiotech.radiotech_backend.security.Role;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import com.radiotech.radiotech_backend.service.TechnicianSkillService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({ "/api/operators/{operatorId}/skills", "/api/v1/operators/{operatorId}/skills" })
public class TechnicianSkillController {

    private final TechnicianSkillService service;

    public TechnicianSkillController(TechnicianSkillService service) {
        this.service = service;
    }

    @GetMapping
    public Object list(@PathVariable String operatorId) throws Exception {
        requireAccess(Permission.USER_READ);
        return service.list(operatorId);
    }

    @GetMapping("/catalog")
    public Object catalog(@PathVariable String operatorId) throws Exception {
        requireAccess(Permission.USER_READ);
        return service.supportedSkills(operatorId);
    }

    @PutMapping("/{skill}")
    public Object upsert(@PathVariable String operatorId, @PathVariable String skill,
            @Valid @RequestBody TechnicianSkillRequest request) throws Exception {
        requireAccess(Permission.USER_UPDATE);
        return service.upsert(operatorId, TechnicianSkillType.fromCode(skill), request);
    }

    @DeleteMapping("/{skill}")
    public void delete(@PathVariable String operatorId, @PathVariable String skill) throws Exception {
        requireAccess(Permission.USER_UPDATE);
        service.delete(operatorId, TechnicianSkillType.fromCode(skill));
    }

    private void requireAccess(Permission permission) {
        Role role = SecurityContextAccessor.currentRole();
        if (!TenantAccessPolicy.canAccess(role, permission)) {
            throw new SecurityException("Permessi insufficienti per la matrice competenze.");
        }
        TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null);
    }
}
