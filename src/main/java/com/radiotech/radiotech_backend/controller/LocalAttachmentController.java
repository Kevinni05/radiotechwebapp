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
 private String tenant(Permission permission){Role role=SecurityContextAccessor.currentRole();boolean allowed=TenantAccessPolicy.canAccess(role,permission)||(permission==Permission.REPORT_READ&&role==Role.OPERATOR&&TenantAccessPolicy.canAccess(role,Permission.REPORT_CREATE));if(!allowed)throw new SecurityException("Operazione non autorizzata.");return TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(),null);}
 @GetMapping("/config") public Map<String,Object> config(){tenant(Permission.REPORT_READ);return Map.of("mode","LOCAL","backend",files.storageBackend(),"maxBytes",LocalAttachmentService.MAX_BYTES);}
 @PostMapping(consumes="application/octet-stream") public Map<String,Object> upload(@RequestParam String operationId,@RequestParam String name,HttpServletRequest request)throws Exception{
  String tenant=tenant(Permission.REPORT_CREATE);
  try(var reservation=files.reserveUpload()) {
   if(request.getContentLengthLong()>LocalAttachmentService.MAX_BYTES)throw new IllegalArgumentException("Allegato troppo grande: limite 10 MB.");
   byte[] data=request.getInputStream().readNBytes(LocalAttachmentService.MAX_BYTES+1);
   LocalAttachmentService.validateUploadContent(name,data);
   return files.save(tenant,SecurityContextAccessor.currentUid(),operationId,name,data);
  }
 }
 @GetMapping("/{id}") public Map<String,Object> download(@PathVariable String id)throws Exception{
  String tenant=tenant(Permission.REPORT_READ);Role role=SecurityContextAccessor.currentRole();return files.read(id,tenant,SecurityContextAccessor.currentUid(),role!=Role.OPERATOR);
 }
 public org.springframework.http.ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> content(String id)throws Exception{return content(id,null);}
 @GetMapping("/{id}/content") public org.springframework.http.ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> content(@PathVariable String id,HttpServletRequest request)throws Exception{
  String tenant=tenant(Permission.REPORT_READ);
  var reservation=files.reserveUpload();
  LocalAttachmentService.VerifiedDownload download;
  try {download=files.prepareDownload(id,tenant,SecurityContextAccessor.currentUid(),SecurityContextAccessor.currentRole()!=Role.OPERATOR);}
  catch(Exception error){reservation.close();throw error;}
  var lease=com.radiotech.radiotech_backend.service.AsyncDownloadLease.bind(request,download,reservation);
  return org.springframework.http.ResponseEntity.ok()
   .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
   .contentLength(download.size()).header("Cache-Control","private, no-store")
   .header("X-Content-Type-Options","nosniff").header("ETag","\""+download.digest()+"\"")
   .header("Content-Disposition",org.springframework.http.ContentDisposition.attachment().filename(download.name(),java.nio.charset.StandardCharsets.UTF_8).build().toString())
   .body(output->{try(lease){download.transferTo(output);}catch(Exception error){throw new java.io.IOException("Download interrotto.",error);}});
 }
}
