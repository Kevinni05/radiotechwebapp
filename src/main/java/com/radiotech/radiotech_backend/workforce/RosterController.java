package com.radiotech.radiotech_backend.workforce;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import jakarta.validation.Valid;
import java.util.*;

@RestController
@RequestMapping("/api/v1/workforce")
public class RosterController {
    private final RosterService roster;private final AttendanceService attendance;
    public RosterController(RosterService roster,AttendanceService attendance){this.roster=roster;this.attendance=attendance;}
    @GetMapping("/roster/members") public Object members() throws Exception{return roster.members();}
    @GetMapping("/roster/{month}") public Object draft(@PathVariable String month) throws Exception{return roster.draft(month);}
    @PutMapping("/roster/{month}") public Object save(@PathVariable String month,@Valid @RequestBody Roster.Config config) throws Exception{return roster.save(month,config);}
    @PostMapping("/roster/{month}/generate") public Object generate(@PathVariable String month,@Valid @RequestBody Roster.Version version) throws Exception{return roster.generate(month,version);}
    @PostMapping("/roster/{month}/publish") public Object publish(@PathVariable String month,@Valid @RequestBody Roster.Version version) throws Exception{return roster.publish(month,version);}
    @PostMapping("/roster/{month}/notify") public Object notifyOperators(@PathVariable String month) throws Exception{return roster.notifyPublished(month);}
    @GetMapping("/me/roster/{month}") public Object personal(@PathVariable String month) throws Exception{return roster.personal(month);}
    @GetMapping("/attendance") public Object attendance(@RequestParam String month,@RequestParam(required=false) String uid) throws Exception{return attendance.report(month,uid,false);}
    @GetMapping("/me/attendance") public Object mine(@RequestParam String month) throws Exception{return attendance.report(month,null,true);}
    @GetMapping("/attendance/{id}/events") public Object events(@PathVariable String id) throws Exception{return attendance.events(id);}
    @PostMapping("/me/attendance/corrections") public Object correction(@Valid @RequestBody AttendanceService.Correction correction) throws Exception{return attendance.correction(correction);}
    @PostMapping("/attendance/{id}/review") public Object review(@PathVariable String id,@Valid @RequestBody AttendanceService.Review review) throws Exception{return attendance.review(id,review);}
    @GetMapping("/me/roster/{month}/pdf") public ResponseEntity<byte[]> myPdf(@PathVariable String month) throws Exception{return pdf(month,null);}
    @GetMapping("/roster/{month}/pdf/{operatorId}") public ResponseEntity<byte[]> operatorPdf(@PathVariable String month,@PathVariable String operatorId) throws Exception{return pdf(month,operatorId);}
    private ResponseEntity<byte[]> pdf(String month,String id) throws Exception {
        var data=roster.forPdf(month,id);return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"RADIOTECH-turni-"+RosterService.month(month)+".pdf\"")
                .body(RosterPdf.render(data));
    }
}
