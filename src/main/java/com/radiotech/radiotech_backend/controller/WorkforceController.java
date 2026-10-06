package com.radiotech.radiotech_backend.controller;
import com.radiotech.radiotech_backend.dto.WorkforceRequests;
import com.radiotech.radiotech_backend.service.WorkforceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/workforce")
public class WorkforceController {
    private final WorkforceService service;
    public WorkforceController(WorkforceService service) { this.service = service; }
    @GetMapping("/me/shift") public Map<String, Object> myShift() throws Exception { return service.myShift(); }
    @PutMapping("/me/shift") public Map<String, Object> shift(@Valid @RequestBody WorkforceRequests.Shift request) throws Exception { return service.changeShift(request); }
    @GetMapping("/shifts") public List<Map<String, Object>> shifts() throws Exception { return service.shifts(); }
    @GetMapping("/signals") public List<Map<String, Object>> signals() throws Exception { return service.signals(); }
    @PostMapping("/signals") public Map<String, Object> signal(@Valid @RequestBody WorkforceRequests.Signal request) throws Exception { return service.submitSignal(request); }
    @PatchMapping("/signals/{id}") public Map<String, Object> review(@PathVariable String id, @Valid @RequestBody WorkforceRequests.Review request) throws Exception { return service.review(id, request); }
}
