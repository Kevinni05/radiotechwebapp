package com.radiotech.radiotech_backend.controller;
import com.radiotech.radiotech_backend.service.LocalAttachmentService;
import com.radiotech.radiotech_backend.security.*;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/v1/files")
public class LocalAttachmentController {
 private final LocalAttachmentService files;
 public LocalAttachmentController(LocalAttachmentService files){this.files=files;}
 private String tenant(Permission permission){if(!TenantAccessPolicy.canAccess(SecurityContextAccessor.currentRole(),permission))throw new SecurityException("Operazione non autorizzata.");return TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(),null);}
 @GetMapping("/config") public Map<String,Object> config(){tenant(Permission.REPORT_READ);return Map.of("mode",files.enabled()?"LOCAL":"FIREBASE","maxBytes",LocalAttachmentService.MAX_BYTES);}
 @PostMapping(consumes="application/octet-stream") public Map<String,Object> upload(@RequestParam String operationId,@RequestParam String name,HttpServletRequest request)throws Exception{
  String tenant=tenant(Permission.REPORT_CREATE);byte[] data=request.getInputStream().readNBytes(LocalAttachmentService.MAX_BYTES+1);return files.save(tenant,SecurityContextAccessor.currentUid(),operationId,name,data);
 }
 @GetMapping("/{id}") public Map<String,Object> download(@PathVariable String id)throws Exception{
  String tenant=tenant(Permission.REPORT_READ);Role role=SecurityContextAccessor.currentRole();return files.read(id,tenant,SecurityContextAccessor.currentUid(),role!=Role.OPERATOR);
 }
}
