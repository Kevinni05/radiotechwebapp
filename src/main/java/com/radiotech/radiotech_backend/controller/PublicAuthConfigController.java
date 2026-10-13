package com.radiotech.radiotech_backend.controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import com.radiotech.radiotech_backend.security.IdentityGateway;
@RestController
public class PublicAuthConfigController {
 @Value("${radiotech.firebase.web-api-key:}") private String key;
 @Value("${app.firebase.project-id:demo-radiotech}") private String project;
 @Value("${radiotech.security.federated-login-enabled:false}") private boolean enabled;
 @Value("${radiotech.firebase.database-url:${FIREBASE_DATABASE_URL:}}") private String databaseUrl;
 @GetMapping("/api/v1/auth/public-config") public Map<String,Object> config(){return Map.of("enabled",!key.isBlank(),"federatedLoginEnabled",enabled&&!key.isBlank(),"apiKey",key,"projectId",project,"authDomain",project+".firebaseapp.com","databaseURL",databaseUrl.trim(),"identityRequired",IdentityGateway.isolated(),"identityOrigin",IdentityGateway.origin());}
}
