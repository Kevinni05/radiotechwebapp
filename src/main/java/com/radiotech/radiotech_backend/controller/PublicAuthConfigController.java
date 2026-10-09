package com.radiotech.radiotech_backend.controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
public class PublicAuthConfigController {
 @Value("${radiotech.firebase.web-api-key:}") private String key;
 @Value("${app.firebase.project-id:demo-radiotech}") private String project;
 @Value("${radiotech.security.federated-login-enabled:false}") private boolean enabled;
 @GetMapping("/api/v1/auth/public-config") public Map<String,Object> config(){return Map.of("enabled",enabled&&!key.isBlank(),"apiKey",enabled?key:"","projectId",project,"authDomain",project+".firebaseapp.com");}
}
