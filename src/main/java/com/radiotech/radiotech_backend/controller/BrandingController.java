package com.radiotech.radiotech_backend.controller;

import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.*;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

/** Tenant-owned presentation settings. No arbitrary CSS, HTML, scripts or external URLs. */
@RestController
@RequestMapping("/api/v1/branding")
public class BrandingController {
    private String tenant() { return TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null); }
    @GetMapping public Map<String,Object> get() throws Exception {
        String tenant=tenant(); var doc=FirestoreClient.getFirestore().collection("tenantBranding").document(tenant).get().get();
        return doc.exists()?doc.getData():Map.of();
    }
    @PutMapping public Map<String,Object> save(@RequestBody Map<String,Object> input) throws Exception {
        String tenant=tenant();
        if(!Set.of(Role.ADMIN,Role.SUPER_ADMIN,Role.CHIEF_EXECUTIVE).contains(SecurityContextAccessor.currentRole())) throw new SecurityException("Solo un amministratore può personalizzare il prodotto aziendale.");
        var result=validate(input);result.put("tenantId",tenant);result.put("updatedAt",Instant.now().toString());
        FirestoreClient.getFirestore().collection("tenantBranding").document(tenant).set(result).get();return result;
    }
    static Map<String,Object> validate(Map<String,Object> input) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String key:List.of("name","subtitle","supportEmail","copyright","welcomeTitle","welcomeDescription")) {
            String value=Objects.toString(input.get(key),"").strip(); if(value.length()>500)throw new IllegalArgumentException("Testo troppo lungo: "+key);result.put(key,value);
        }
        String logo=Objects.toString(input.get("logo"),"");
        if(!logo.isEmpty() && (!logo.matches("^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$") || logo.length()>350000))throw new IllegalArgumentException("Logo non valido. Usa PNG, JPEG o WebP fino a 250 KB.");
        result.put("logo",logo);
        Map<String,String> colors=new LinkedHashMap<>();
        if(input.get("colors") instanceof Map<?,?> supplied)for(var e:supplied.entrySet()) {
            String key=e.getKey().toString(),value=Objects.toString(e.getValue(),"");
            String baseKey=key.startsWith("light")&&key.length()>5?Character.toLowerCase(key.charAt(5))+key.substring(6):key;
            if(!Set.of("accent","background","surface","text","muted","border","success","warning","danger").contains(baseKey)||!value.matches("#[a-fA-F0-9]{6}"))throw new IllegalArgumentException("Colore non valido.");colors.put(key,value);
        }
        result.put("colors",colors);
        Map<String,String> labels=new LinkedHashMap<>();
        if(input.get("labels") instanceof Map<?,?> supplied) {
            if(supplied.size()>1200)throw new IllegalArgumentException("Troppe etichette.");
            for(var e:supplied.entrySet()) {String key=e.getKey().toString(),value=Objects.toString(e.getValue(),"").strip();if(!key.matches("[a-zA-Z0-9_.:-]{1,120}")||value.length()>500)throw new IllegalArgumentException("Etichetta non valida.");if(!value.isBlank())labels.put(key,value);}
        }
        result.put("labels",labels);
        long bytes=4096;
        for(Object value:result.values())if(value instanceof String text)bytes+=text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        for(var label:labels.entrySet())bytes+=label.getKey().length()+label.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8).length+40;
        if(bytes>800000)throw new IllegalArgumentException("Configurazione troppo grande. Riduci logo o testi.");
        return result;
    }
}
