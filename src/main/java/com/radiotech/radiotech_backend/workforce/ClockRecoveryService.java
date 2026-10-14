package com.radiotech.radiotech_backend.workforce;

import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.dto.WorkforceRequests;
import com.radiotech.radiotech_backend.service.WorkforceService;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import static com.radiotech.radiotech_backend.workforce.RosterService.*;

/** Conflicted device events are transferred before the queue can be cleared. */
@Service
public class ClockRecoveryService {
    public record Request(@Valid @NotEmpty @Size(max=100) List<WorkforceRequests.Shift> events) {}
    public record Interval(@NotBlank String startedAt,@NotBlank String endedAt,@Min(0) @Max(1440) int breakMinutes,
            @NotBlank @Size(max=1000) String reason,@Min(0) long expectedVersion) {}
    public record Close(@NotBlank @Size(max=1000) String reason,@Min(0) long expectedVersion) {}
    private final JsonMapper json=JsonMapper.builder().build();
    @SuppressWarnings("unchecked") public Map<String,Object> archive(Request request) throws Exception {
        String tenant=tenant(),uid=uid();for(var event:request.events())if(event.occurredAt()!=null)Instant.parse(event.occurredAt());
        String payload=json.writeValueAsString(request.events()),id=key(tenant+":"+uid+":"+payload);var db=FirestoreClient.getFirestore();var ref=db.collection("workforceClockRecoveries").document(id);
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getDetails();
        String name=auth instanceof com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails d && d.getDisplayName()!=null?d.getDisplayName():uid;
        var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("tenantId",tenant);row.put("uid",uid);row.put("name",name);
        row.put("events",json.convertValue(request.events(),List.class));row.put("createdAt",Instant.now().toString());row.put("status","PENDING");row.put("version",0L);
        transact(db.runTransaction(tx->{var old=tx.get(ref).get();if(!old.exists())tx.create(ref,row);return null;}));
        return Map.of("id",id,"notice","Timbrature consegnate al responsabile per la riconciliazione.");
    }
    public List<Map<String,Object>> pending() throws Exception {
        WorkforceService.requireManager();return FirestoreClient.getFirestore().collection("workforceClockRecoveries").whereEqualTo("tenantId",tenant()).orderBy("createdAt",com.google.cloud.firestore.Query.Direction.DESCENDING).limit(200).get().get().getDocuments().stream().map(d->(Map<String,Object>)new LinkedHashMap<>(d.getData())).toList();
    }
    public Map<String,Object> interval(String id,Interval request) throws Exception {
        WorkforceService.requireManager();String tenant=tenant(),actor=uid();if(!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("ID non valido.");
        long work=AttendanceService.validateTimes(request.startedAt(),request.endedAt(),request.breakMinutes());var db=FirestoreClient.getFirestore();var ref=db.collection("workforceClockRecoveries").document(id);
        return transact(db.runTransaction(tx->{
            var recovery=tx.get(ref).get();if(!recovery.exists()||!tenant.equals(recovery.getString("tenantId")))throw new SecurityException("Richiesta non autorizzata.");
            if("RESOLVED".equals(recovery.getString("status")))throw new IllegalArgumentException("Richiesta già conclusa.");
            if(number(recovery.get("version"))!=request.expectedVersion())throw conflict();
            String owner=recovery.getString("uid"),sessionId=key(id+":"+request.startedAt()+":"+request.endedAt());var session=db.collection("workforceAttendance").document(sessionId);var existing=tx.get(session).get();
            if(existing.exists())throw new IllegalArgumentException("Intervallo già registrato per questa richiesta.");
            Instant start=Instant.parse(request.startedAt()),end=Instant.parse(request.endedAt());
            // Avoid silently adding a second copy of already registered or approved work.
            var candidates=tx.get(db.collection("workforceAttendance").whereEqualTo("tenantId",tenant).whereEqualTo("uid",owner)
                    .whereGreaterThanOrEqualTo("startedTime",com.google.cloud.Timestamp.ofTimeSecondsAndNanos(start.minus(Duration.ofDays(2)).getEpochSecond(),0))
                    .whereLessThan("startedTime",com.google.cloud.Timestamp.ofTimeSecondsAndNanos(end.getEpochSecond(),end.getNano()))).get();
            var active=tx.get(db.collection("workforceShifts").document(key(tenant+":"+owner))).get();
            if(active.exists()&&!"OFF_DUTY".equals(active.getString("status"))&&Instant.parse(active.getString("startedAt")).isBefore(end))throw new IllegalArgumentException("Intervallo sovrapposto a un turno ancora aperto. Chiudi e verifica prima quella sessione.");
            for(var candidate:candidates.getDocuments()) {
                Map<String,Object> effective=candidate.get("approved") instanceof Map<?,?> a?(Map<String,Object>)a:candidate.getData();
                Instant oldStart=Instant.parse(effective.get("startedAt").toString());Instant oldEnd=Instant.parse(Objects.toString(effective.get("endedAt"),Instant.now().toString()));
                if(start.isBefore(oldEnd)&&end.isAfter(oldStart))throw new IllegalArgumentException("Intervallo sovrapposto a una presenza esistente. Correggi o valida quella sessione.");
            }
            var row=new LinkedHashMap<String,Object>();row.put("tenantId",tenant);row.put("uid",owner);row.put("name",recovery.getString("name"));row.put("id",sessionId);
            row.put("startedAt",request.startedAt());row.put("endedAt",request.endedAt());row.put("startedTime",com.google.cloud.Timestamp.ofTimeSecondsAndNanos(start.getEpochSecond(),start.getNano()));row.put("status","OFF_DUTY");
            row.put("workMinutes",work);row.put("breakMinutes",request.breakMinutes());row.put("reviewStatus","APPROVED");row.put("version",1L);row.put("recoveryId",id);row.put("source","MANUAL_RECOVERY");
            row.put("approved",Map.of("startedAt",request.startedAt(),"endedAt",request.endedAt(),"workMinutes",work,"breakMinutes",request.breakMinutes()));row.put("reviewedBy",actor);row.put("reviewReason",request.reason());row.put("reviewedAt",Instant.now().toString());
            tx.create(session,row);tx.update(ref,Map.of("status","IN_REVIEW","version",request.expectedVersion()+1));
            tx.create(db.collection("workforceAttendanceAudit").document(),Map.of("tenantId",tenant,"sessionId",sessionId,"action","MANUAL_RECOVERY","actor",actor,"reason",request.reason(),"recoveryId",id,"createdAt",row.get("reviewedAt")));
            for(String action:List.of("MANUAL_START","MANUAL_END"))tx.create(db.collection("workforceTimeEvents").document(key(sessionId+":"+action)),Map.of("tenantId",tenant,"uid",owner,"sessionId",sessionId,"action",action,"source","MANUAL_RECOVERY","recordedAt",row.get("reviewedAt"),"occurredAt",action.equals("MANUAL_START")?request.startedAt():request.endedAt(),"version",action.equals("MANUAL_START")?1L:2L));
            return Map.of("id",sessionId,"notice","Intervallo validato e registrato con provenienza manuale.");
        }));
    }
    public Map<String,Object> close(String id,Close request) throws Exception {
        WorkforceService.requireManager();String tenant=tenant(),actor=uid();if(!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("ID non valido.");var db=FirestoreClient.getFirestore();var ref=db.collection("workforceClockRecoveries").document(id);
        transact(db.runTransaction(tx->{var doc=tx.get(ref).get();if(!doc.exists()||!tenant.equals(doc.getString("tenantId")))throw new SecurityException("Richiesta non autorizzata.");if("RESOLVED".equals(doc.getString("status")))throw new IllegalArgumentException("Richiesta già conclusa.");if(number(doc.get("version"))!=request.expectedVersion())throw conflict();tx.create(db.collection("workforceAttendanceAudit").document(),Map.of("tenantId",tenant,"recoveryId",id,"action","RECOVERY_RESOLVED","actor",actor,"reason",request.reason(),"createdAt",Instant.now().toString()));tx.update(ref,Map.of("status","RESOLVED","version",request.expectedVersion()+1,"resolution",request.reason(),"resolvedBy",actor,"resolvedAt",Instant.now().toString()));return null;}));return Map.of("notice","Riconciliazione conclusa.");
    }
}
