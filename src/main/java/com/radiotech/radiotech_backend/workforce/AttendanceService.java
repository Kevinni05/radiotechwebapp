package com.radiotech.radiotech_backend.workforce;

import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.service.WorkforceService;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import static com.radiotech.radiotech_backend.workforce.RosterService.*;

@Service
public class AttendanceService {
    public record Correction(@NotBlank @Pattern(regexp="[a-f0-9]{64}") String sessionId,
            @NotBlank String startedAt,@NotBlank String endedAt,@Min(0) @Max(1440) int breakMinutes,
            @NotBlank @Size(max=1000) String reason,@Min(0) long expectedVersion) {}
    public record Review(@NotBlank @Pattern(regexp="APPROVE|REJECT") String decision,
            @NotBlank @Size(max=1000) String reason,@Min(0) long expectedVersion) {}
    private Map<String,Object> ownSession(String id,String tenant,String uid) throws Exception {
        var doc=FirestoreClient.getFirestore().collection("workforceAttendance").document(id).get().get();
        if(!doc.exists()||!tenant.equals(doc.getString("tenantId"))||!uid.equals(doc.getString("uid")))throw new SecurityException("Registrazione non autorizzata.");
        return new LinkedHashMap<>(doc.getData());
    }
    public Map<String,Object> correction(Correction c) throws Exception {
        String tenant=tenant(),uid=uid();validateTimes(c.startedAt(),c.endedAt(),c.breakMinutes());
        var db=FirestoreClient.getFirestore(); var ref=db.collection("workforceAttendance").document(c.sessionId());
        return transact(db.runTransaction(tx->{
            var doc=tx.get(ref).get();
            if(!doc.exists()||!tenant.equals(doc.getString("tenantId"))||!uid.equals(doc.getString("uid")))throw new SecurityException("Registrazione non autorizzata.");
            if(!"OFF_DUTY".equals(doc.getString("status")))throw new IllegalArgumentException("Chiudi il turno prima di richiedere una correzione.");
            if(number(doc.get("version"))!=c.expectedVersion())throw conflict();
            if("PENDING".equals(doc.getString("reviewStatus")))throw new IllegalArgumentException("Una richiesta è già in attesa.");
            var request=Map.<String,Object>of("startedAt",c.startedAt(),"endedAt",c.endedAt(),"breakMinutes",c.breakMinutes(),"reason",c.reason(),"requestedBy",uid,"requestedAt",Instant.now().toString());
            tx.update(ref,Map.of("correction",request,"reviewStatus","PENDING","version",c.expectedVersion()+1));
            tx.create(db.collection("workforceAttendanceAudit").document(),Map.of("tenantId",tenant,"sessionId",c.sessionId(),"action","CORRECTION_REQUEST","actor",uid,"request",request));
            return request;
        }));
    }
    static long validateTimes(String from,String to,long breaks) {
        long minutes=Duration.between(Instant.parse(from),Instant.parse(to)).toMinutes();
        if(!Instant.parse(to).isAfter(Instant.parse(from))||minutes>2880||breaks<0||breaks>minutes||Instant.parse(to).isAfter(Instant.now().plusSeconds(300)))throw new IllegalArgumentException("Orari o pause non validi.");
        return minutes-breaks;
    }
    public Map<String,Object> review(String id,Review review) throws Exception {
        WorkforceService.requireManager();String tenant=tenant(),actor=uid();if(!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("ID non valido.");
        var db=FirestoreClient.getFirestore();var ref=db.collection("workforceAttendance").document(id);
        return transact(db.runTransaction(tx->{
            var doc=tx.get(ref).get();if(!doc.exists()||!tenant.equals(doc.getString("tenantId")))throw new SecurityException("Registrazione non autorizzata.");
            if(number(doc.get("version"))!=review.expectedVersion())throw conflict();
            if(!"OFF_DUTY".equals(doc.getString("status")))throw new IllegalArgumentException("Il turno non è concluso.");
            var row=new LinkedHashMap<>(doc.getData());
            if("APPROVE".equals(review.decision())) {
                Map<String,Object> effective="PENDING".equals(row.get("reviewStatus"))&&row.get("correction") instanceof Map<?,?> c?new LinkedHashMap<>((Map<String,Object>)c):row;
                long work=validateTimes(effective.get("startedAt").toString(),effective.get("endedAt").toString(),number(effective.get("breakMinutes")));
                if(effective==row)work=number(row.get("workMinutes"));
                row.put("approved",Map.of("startedAt",effective.get("startedAt"),"endedAt",effective.get("endedAt"),"breakMinutes",number(effective.get("breakMinutes")),"workMinutes",work));
                row.put("reviewStatus","APPROVED");
            } else { if(!"PENDING".equals(row.get("reviewStatus")))throw new IllegalArgumentException("Nessuna correzione in attesa.");row.put("reviewStatus",row.containsKey("approved")?"APPROVED":"REJECTED"); }
            row.put("reviewedBy",actor);row.put("reviewReason",review.reason());row.put("reviewedAt",Instant.now().toString());row.put("version",review.expectedVersion()+1);
            tx.set(ref,row);tx.create(db.collection("workforceAttendanceAudit").document(),Map.of("tenantId",tenant,"sessionId",id,"action",review.decision(),"actor",actor,"reason",review.reason(),"createdAt",row.get("reviewedAt"),"before",doc.getData(),"after",row));
            var response=new LinkedHashMap<>(row);response.remove("startedTime");return response;
        }));
    }
    public Map<String,Object> report(String value,String operatorUid,boolean personal) throws Exception {
        if(!personal)WorkforceService.requireManager();String tenant=tenant();YearMonth month=month(value);
        String filter=personal?uid():Objects.toString(operatorUid,"");
        var plan=personal?personalPlan(tenant,value,filter):published(tenant,value);
        String zone=Objects.toString(plan.get("zone"),"Europe/Rome"); ZoneId tz=ZoneId.of(zone);
        Instant from=month.atDay(1).atStartOfDay(tz).toInstant(),to=month.plusMonths(1).atDay(1).atStartOfDay(tz).toInstant();
        Query query=FirestoreClient.getFirestore().collection("workforceAttendance").whereEqualTo("tenantId",tenant);
        if(!filter.isBlank())query=query.whereEqualTo("uid",filter);
        // Include cross-midnight sessions that began before the month.
        query=query.whereGreaterThanOrEqualTo("startedTime",com.google.cloud.Timestamp.ofTimeSecondsAndNanos(from.minus(Duration.ofDays(2)).getEpochSecond(),0)).whereLessThan("startedTime",com.google.cloud.Timestamp.ofTimeSecondsAndNanos(to.getEpochSecond(),0)).orderBy("startedTime").limit(2001);
        var documents=query.get().get().getDocuments();
        if(documents.size()>2000)throw new IllegalArgumentException("Troppi risultati: seleziona un operatore per ottenere il registro completo.");
        var sessions=new ArrayList<Map<String,Object>>();
        for(var doc:documents) {
            var row=new LinkedHashMap<>(doc.getData()); row.remove("startedTime"); Instant end=Instant.parse(Objects.toString(row.get("endedAt"),Instant.now().toString()));
            if(!end.isAfter(from))continue;
            if(!"OFF_DUTY".equals(row.get("status"))) { long delta=Math.max(0,Duration.between(Instant.parse(row.get("lastTransitionAt").toString()),Instant.now()).toMinutes());if("ACTIVE".equals(row.get("status")))row.put("workMinutes",number(row.get("workMinutes"))+delta);else row.put("breakMinutes",number(row.get("breakMinutes"))+delta); }
            row.put("id",doc.getId());row.put("boundarySession",Instant.parse(row.get("startedAt").toString()).isBefore(from));sessions.add(row);
        }
        var planned=rows(plan,"slots").stream().filter(s->filter.isBlank()||filter.equals(s.get("uid"))).toList();
        var anomalies=reconcile(planned,sessions,(int)number(plan.get("toleranceMinutes")),Instant.now());
        long registered=sessions.stream().filter(s->!Boolean.TRUE.equals(s.get("boundarySession"))).mapToLong(s->number(s.get("workMinutes"))).sum();
        long approved=sessions.stream().filter(s->!Boolean.TRUE.equals(s.get("boundarySession"))).mapToLong(s->s.get("approved") instanceof Map<?,?> a?number(a.get("workMinutes")):0).sum();
        var result=new LinkedHashMap<String,Object>();result.put("month",value);result.put("zone",zone);result.put("sessions",sessions);result.put("anomalies",anomalies);
        result.put("plannedMinutes",planned.stream().mapToLong(s->number(s.get("workMinutes"))).sum());result.put("registeredMinutes",registered);result.put("approvedMinutes",approved);result.put("publishedRevision",number(plan.get("revision")));result.put("notice","Totali delle sessioni iniziate nel periodo, inclusi i turni a cavallo del mese; nessuno straordinario è approvato automaticamente.");return result;
    }
    private Map<String,Object> personalPlan(String tenant,String month,String uid) throws Exception {var p=published(tenant,month);if(p.isEmpty())return p;p.put("slots",rows(p,"slots").stream().filter(s->uid.equals(s.get("uid"))).toList());return p;}
    public List<Map<String,Object>> events(String id) throws Exception {
        String tenant=tenant();if(!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("ID non valido.");
        var doc=FirestoreClient.getFirestore().collection("workforceAttendance").document(id).get().get();
        if(!doc.exists()||!tenant.equals(doc.getString("tenantId"))||!WorkforceService.manager()&&!uid().equals(doc.getString("uid")))throw new SecurityException("Registrazione non autorizzata.");
        return FirestoreClient.getFirestore().collection("workforceTimeEvents").whereEqualTo("tenantId",tenant).whereEqualTo("sessionId",id).orderBy("version").get().get().getDocuments().stream().map(d->new LinkedHashMap<>(d.getData())).map(m->(Map<String,Object>)m).toList();
    }
    public static List<Map<String,Object>> reconcile(List<Map<String,Object>> slots,List<Map<String,Object>> sessions,int tolerance,Instant now) {
        var anomalies=new ArrayList<Map<String,Object>>();var matched=new HashSet<String>();var usedSlots=new HashSet<Integer>();
        // Match each session once, against the closest planned start within 12 hours.
        for(var session:sessions) {
            String sid=Objects.toString(session.get("id"),"");if(Boolean.TRUE.equals(session.get("delayedSync")))anomalies.add(anomaly("DELAYED_SYNC",session,"Timbrature sincronizzate in ritardo: verifica gli orari del dispositivo.",sid));Map<String,Object> actual=session.get("approved") instanceof Map<?,?> a?(Map<String,Object>)a:session;
            Instant started=Instant.parse(actual.get("startedAt").toString()); int best=-1;long distance=Long.MAX_VALUE;
            for(int i=0;i<slots.size();i++) {
                var slot=slots.get(i);if(usedSlots.contains(i)||!Objects.equals(slot.get("uid"),session.get("uid")))continue;
                long delta=Math.abs(Duration.between(Instant.parse(slot.get("startAt").toString()),started).toMinutes());
                if(delta<=720&&delta<distance){best=i;distance=delta;}
            }
            if(best<0)anomalies.add(anomaly("UNPLANNED",session,"Presenza senza turno associato.",sid));
            else {
                usedSlots.add(best);matched.add(sid);var slot=slots.get(best);
                long late=Duration.between(Instant.parse(slot.get("startAt").toString()),started).toMinutes();
                if(late>tolerance)anomalies.add(anomaly("LATE",session,"Entrata in ritardo di "+late+" minuti.",sid));
                if(actual.get("endedAt")!=null) {
                    long early=Duration.between(Instant.parse(actual.get("endedAt").toString()),Instant.parse(slot.get("endAt").toString())).toMinutes();
                    if(early>tolerance)anomalies.add(anomaly("EARLY_EXIT",session,"Uscita anticipata di "+early+" minuti.",sid));
                    if(early < -tolerance)anomalies.add(anomaly("EXTRA_TIME",session,"Prolungamento di "+(-early)+" minuti da verificare.",sid));
                    long diff=number(actual.get("workMinutes"))-number(slot.get("workMinutes"));
                    if(Math.abs(diff)>tolerance)anomalies.add(anomaly("DURATION",session,"Scostamento durata: "+diff+" minuti.",sid));
                } else if(now.isAfter(Instant.parse(slot.get("endAt").toString()).plusSeconds(tolerance*60L)))anomalies.add(anomaly("MISSING_EXIT",session,"Uscita non registrata dopo la fine prevista.",sid));
            }
            if(actual.get("endedAt")==null&&Duration.between(started,now).toHours()>=24)anomalies.add(anomaly("OPEN_SESSION",session,"Turno aperto da oltre 24 ore.",sid));
        }
        for(int i=0;i<slots.size();i++)if(!usedSlots.contains(i)) {
            var slot=slots.get(i);if(now.isAfter(Instant.parse(slot.get("startAt").toString()).plusSeconds(tolerance*60L)))anomalies.add(anomaly("MISSING_ENTRY",slot,"Entrata non registrata: presenza da verificare.",""));
        }
        return anomalies;
    }
    private static Map<String,Object> anomaly(String type,Map<String,Object> row,String message,String sid) {return Map.of("type",type,"uid",Objects.toString(row.get("uid"),""),"name",Objects.toString(row.get("name"),""),"sessionId",sid,"date",Objects.toString(row.get("date"),Objects.toString(row.get("startedAt"),"")),"message",message);}
}
