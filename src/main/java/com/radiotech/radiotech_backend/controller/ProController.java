package com.radiotech.radiotech_backend.controller;
import com.radiotech.radiotech_backend.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/pro")
public class ProController {
    private final ProService service;
    public ProController(ProService service) { this.service=service; }
    @GetMapping("/catalog") public List<ProCatalog.Module> catalog() { return service.catalog(); }
    @GetMapping("/task-packs/{id}") public Map<String,Object> pack(@PathVariable String id) throws Exception { return service.taskPack(id); }
    @GetMapping("/options/{target}") public List<Map<String,Object>> options(@PathVariable String target) throws Exception { return service.options(target); }
    @GetMapping("/{module}") public Map<String,Object> list(@PathVariable String module) throws Exception { return service.list(module); }
    @PostMapping("/{module}") public Map<String,Object> create(@PathVariable String module,@RequestBody ProService.Change request) throws Exception { return service.save(module,null,request); }
    @PutMapping("/{module}/{id}") public Map<String,Object> update(@PathVariable String module,@PathVariable String id,@RequestBody ProService.Change request) throws Exception { return service.save(module,id,request); }
    @PostMapping("/{module}/{id}/actions") public Map<String,Object> action(@PathVariable String module,@PathVariable String id,@RequestBody ProService.Action request) throws Exception { return service.action(module,id,request); }
}
