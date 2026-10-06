package com.radiotech.radiotech_backend.controller;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import java.nio.file.*;

/** Serves only the explicitly prepared test APK; disabled in production. */
@RestController
public class TestApkController {
 private final Environment environment;
 public TestApkController(Environment environment){this.environment=environment;}
 @GetMapping("/assets/downloads/RadioTech-enterprise-pro-test.apk")
 public ResponseEntity<FileSystemResource> download(){Path file=Path.of(".dist/releases/RadioTech-enterprise-pro-test.apk");if(environment.acceptsProfiles(Profiles.of("production"))||!Files.isRegularFile(file))return ResponseEntity.notFound().build();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("application/vnd.android.package-archive")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=RadioTech-enterprise-pro-test.apk").body(new FileSystemResource(file));}
}
