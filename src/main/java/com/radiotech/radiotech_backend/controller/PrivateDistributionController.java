package com.radiotech.radiotech_backend.controller;
import com.radiotech.radiotech_backend.service.PrivateDistributionService;
import com.radiotech.radiotech_backend.security.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.util.*;

@RestController @RequestMapping("/api/v1/distribution")
public class PrivateDistributionController {
    private final PrivateDistributionService releases;
    private final java.util.concurrent.Semaphore downloads=new java.util.concurrent.Semaphore(2);
    public PrivateDistributionController(PrivateDistributionService releases){this.releases=releases;}
    @org.springframework.beans.factory.annotation.Value("${radiotech.reports.public-base-url:}") private String publicOrigin="";
    private String tenant(){if(SecurityContextAccessor.currentRole()==Role.NONE||SecurityContextAccessor.currentRole()==Role.CUSTOMER)throw new SecurityException("Accesso non autorizzato.");return TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(),null);}
    @GetMapping("/latest.json") public ResponseEntity<Map<String,Object>> latest(){
        try{var body=releases.latest(tenant());if(Boolean.TRUE.equals(body.get("available"))){String origin=publicOrigin.isBlank()?org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString():publicOrigin.replaceAll("/+$","");body.put("downloadUrl",origin+"/api/v1/distribution/"+body.get("filename"));}return ResponseEntity.ok().header("Cache-Control","private, no-store").body(body);}
        catch(SecurityException error){throw error;}catch(Exception error){throw new IllegalStateException("Canale aggiornamenti temporaneamente non disponibile.",error);}
    }
    @GetMapping("/{filename:.+\\.apk}") public ResponseEntity<StreamingResponseBody> download(@PathVariable String filename,jakarta.servlet.http.HttpServletRequest request)throws Exception {
        String tenant=tenant();if(!downloads.tryAcquire())throw new org.springframework.web.server.ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Download occupati. Riprova.");
        PrivateDistributionService.Download file;
        try{file=releases.prepare(tenant,filename);}catch(Exception error){downloads.release();throw error;}
        var lease=com.radiotech.radiotech_backend.service.AsyncDownloadLease.bind(request,file,downloads::release);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(file.size())
            .header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff")
            .header("ETag","\""+file.digest()+"\"").header("Content-Disposition",ContentDisposition.attachment().filename(file.filename()).build().toString())
            .body(output->{try(lease;var input=java.nio.file.Files.newInputStream(file.file())){input.transferTo(output);}catch(Exception error){throw new java.io.IOException("Download interrotto.",error);}});
    }
}
