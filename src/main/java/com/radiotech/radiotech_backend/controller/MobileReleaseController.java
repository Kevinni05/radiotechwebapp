package com.radiotech.radiotech_backend.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;

@RestController
public class MobileReleaseController {
    @GetMapping("/api/v1/mobile/releases/latest")
    public Map<String,Object> latest() throws Exception {
        try (var input = new ClassPathResource("mobile-release.json").getInputStream()) {
            return JsonMapper.builder().build().readValue(input, Map.class);
        }
    }
}
