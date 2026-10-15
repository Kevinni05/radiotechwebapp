package com.radiotech.radiotech_backend.dossier;

import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class DossierController {
  private final DossierService service;

  public DossierController(DossierService service) {
    this.service = service;
  }

  @ModelAttribute
  public void privateResponse(jakarta.servlet.http.HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Referrer-Policy", "no-referrer");
  }

  @GetMapping("/api/v1/antennas/{id}/dossier/graph")
  public Map<String, Object> graph(@PathVariable String id) throws Exception {
    return service.graph(id);
  }

  @GetMapping("/api/v1/antennas/{id}/dossier")
  public Map<String, Object> get(@PathVariable String id) throws Exception {
    return service.get(id);
  }

  @PostMapping("/api/v1/antennas/{id}/dossier/changes")
  public Map<String, Object> change(
      @PathVariable String id, @RequestBody DossierService.Change change) throws Exception {
    return service.change(id, change);
  }

  @PostMapping("/api/v1/antennas/{id}/dossier/reviews")
  public Map<String, Object> review(
      @PathVariable String id, @RequestBody DossierService.Review review) throws Exception {
    return service.review(id, review);
  }

  @GetMapping("/api/v1/antennas/{id}/dossier/files/{fileId}")
  public Map<String, Object> file(@PathVariable String id, @PathVariable String fileId)
      throws Exception {
    return service.file(id, fileId);
  }

  @GetMapping("/api/v1/antennas/{id}/dossier/shares")
  public List<Map<String, Object>> shares(@PathVariable String id) throws Exception {
    return service.shares(id);
  }

  @PostMapping("/api/v1/antennas/{id}/dossier/shares")
  public Map<String, Object> share(@PathVariable String id, @RequestBody DossierService.Share share)
      throws Exception {
    return service.createShare(id, share);
  }

  @DeleteMapping("/api/v1/antennas/{id}/dossier/shares/{key}")
  public void revoke(@PathVariable String id, @PathVariable String key) throws Exception {
    service.revoke(id, key);
  }

  @GetMapping("/api/v1/antennas/{id}/dossier/shares/{key}/accesses")
  public List<Map<String, Object>> accesses(@PathVariable String id, @PathVariable String key)
      throws Exception {
    return service.shareAccesses(id, key);
  }

  @PostMapping("/api/v1/dossier-shares/read")
  public ResponseEntity<?> read(@RequestBody DossierService.PublicRead request) throws Exception {
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("Referrer-Policy", "no-referrer")
        .body(service.publicRead(request));
  }

  @GetMapping("/api/v1/antennas/{id}/dossier/sessions")
  public List<Map<String, Object>> sessions(@PathVariable String id) throws Exception {
    return service.sessions(id);
  }

  @PostMapping("/api/v1/antennas/{id}/dossier/sessions")
  public Map<String, Object> createSession(
      @PathVariable String id, @RequestBody DossierService.SessionCreate request) throws Exception {
    return service.createSession(id, request);
  }

  @GetMapping("/api/v1/radio-pair-sessions/{key}")
  public Map<String, Object> session(@PathVariable String key) throws Exception {
    return service.session(key);
  }

  @PostMapping("/api/v1/radio-pair-sessions/{key}/trials")
  public Map<String, Object> trial(
      @PathVariable String key, @RequestBody DossierService.Trial request) throws Exception {
    return service.trial(key, request);
  }
}
