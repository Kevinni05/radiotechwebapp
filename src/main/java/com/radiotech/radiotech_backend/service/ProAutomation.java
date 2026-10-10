package com.radiotech.radiotech_backend.service;
import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.util.*;
@Component
@EnableScheduling
@ConditionalOnProperty(name="app.firebase.enabled",havingValue="true",matchIfMissing=true)
public class ProAutomation {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ProAutomation.class);
 private final ProService service;private final NotificationService notifications;private final ProWebhookService webhooks;
 private final Map<String,String> cursors=new HashMap<>();
 private volatile Instant quotaRetryAt=Instant.EPOCH;
 private boolean quotaPaused(){return Instant.now().isBefore(quotaRetryAt);}
 private boolean pauseForQuota(Exception error){if(!com.radiotech.radiotech_backend.exception.CloudQuota.exhausted(error))return false;quotaRetryAt=Instant.now().plusSeconds(1800);log.warn("Quota cloud esaurita: automazioni riprovate fra 30 minuti.");return true;}
 public ProAutomation(ProService service,NotificationService notifications,ProWebhookService webhooks){this.service=service;this.notifications=notifications;this.webhooks=webhooks;}
 @Scheduled(fixedDelayString="${radiotech.pro.scan-delay-ms:300000}",initialDelay=60000)
 public void scan(){if(FirebaseApp.getApps().isEmpty()||quotaPaused())return;var db=FirestoreClient.getFirestore();
  for(String collection:List.of("pro_plans","pro_sla","notificationHistory","proWebhookOutbox"))try{
   Query query=db.collection(collection).orderBy(FieldPath.documentId()).limit(50);String cursor=cursors.get(collection);if(cursor!=null)query=query.startAfter(cursor);
   var docs=query.get().get().getDocuments();if(docs.size()<50)cursors.remove(collection);else cursors.put(collection,docs.getLast().getId());
   for(var doc:docs){String tenant=doc.getString("tenantId");if(tenant==null||tenant.isBlank())continue;var previous=SecurityContextHolder.getContext();var context=SecurityContextHolder.createEmptyContext();var auth=new UsernamePasswordAuthenticationToken("SYSTEM_PRO",null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));auth.setDetails(new FirebaseAuthenticationDetails("SYSTEM_PRO",null,"Automazione",tenant));context.setAuthentication(auth);SecurityContextHolder.setContext(context);
    try{
     if(collection.equals("pro_plans")&&"ACTIVE".equals(doc.getString("status"))&&!Instant.parse(doc.getString("nextDueAt")).isAfter(Instant.now()))service.action("plans",doc.getId(),new ProService.Action(((Number)doc.get("version")).longValue(),"auto_"+doc.getId()+"_"+doc.get("version"),"GENERATE",0,""));
     if(collection.equals("pro_sla"))escalate(db,doc.getReference(),tenant);
     if(collection.equals("proWebhookOutbox")&&!Set.of("DELIVERED","FAILED").contains(doc.getString("status")))webhooks.deliver(doc.getId());
     if(collection.equals("notificationHistory")&&Set.of("PENDING","SENDING","PARTIAL").contains(doc.getString("deliveryStatus")==null?"":doc.getString("deliveryStatus")))notifications.deliverRecorded(doc.getId());
    }catch(Exception error){log.warn("Enterprise automation failed: collection={}, resource={}",collection,doc.getId(),error);}finally{SecurityContextHolder.setContext(previous);}
   }
  }catch(Exception error){if(pauseForQuota(error))return;log.warn("Enterprise scan failed: collection={}",collection,error);}
 }
 @Scheduled(fixedDelayString="${radiotech.calendar.scan-delay-ms:15000}",initialDelay=20000)
 public void scanCalendar() {
  if(FirebaseApp.getApps().isEmpty()||quotaPaused())return;
  try {var db=FirestoreClient.getFirestore();Query pending=db.collection("calendarNotes").whereEqualTo("reminderPending",true).orderBy(FieldPath.documentId()).limit(500);String cursor=cursors.get("calendarNotes");if(cursor!=null)pending=pending.startAfter(cursor);var batch=pending.get().get().getDocuments();if(batch.size()<500)cursors.remove("calendarNotes");else cursors.put("calendarNotes",batch.getLast().getId());for(var doc:batch) {
   String tenant=doc.getString("tenantId");if(tenant==null||tenant.isBlank())continue;
   var previous=SecurityContextHolder.getContext();var context=SecurityContextHolder.createEmptyContext();var auth=new UsernamePasswordAuthenticationToken("SYSTEM_CALENDAR",null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));auth.setDetails(new FirebaseAuthenticationDetails("SYSTEM_CALENDAR",null,"Promemoria",tenant));context.setAuthentication(auth);SecurityContextHolder.setContext(context);
   try {calendarReminder(db,doc.getReference(),tenant);}catch(Exception e){log.warn("Promemoria non completato: {}",doc.getId(),e);}finally{SecurityContextHolder.setContext(previous);}
  }}catch(Exception e){if(!pauseForQuota(e))log.warn("Calendario temporaneamente non disponibile",e);}
 }
 private void calendarReminder(Firestore db,DocumentReference ref,String tenant)throws Exception {
  String notificationId=db.runTransaction(tx->{var doc=tx.get(ref).get();
   if(!doc.exists()||!tenant.equals(doc.getString("tenantId"))||!Boolean.TRUE.equals(doc.getBoolean("reminderPending"))||doc.getString("reminderAt")==null||Instant.parse(doc.getString("reminderAt")).isAfter(Instant.now()))return null;
   String id="calendar_"+ref.getId()+"_"+doc.getString("revision");
   tx.set(db.collection("notificationHistory").document(id),Map.of("tenantId",tenant,"title","Promemoria · "+doc.getString("day"),"message",doc.getString("note"),"target",doc.getString("target"),"type","CALENDAR_REMINDER","ownerUid",doc.getString("ownerUid"),"createdAt",Instant.now().toString(),"deliveryStatus","PENDING","delivered",0));
   tx.update(ref,Map.of("reminderPending",false,"reminderSentAt",Instant.now().toString()));return id;
  }).get();
  if(notificationId!=null)notifications.deliverRecorded(notificationId);
 }
 private void escalate(Firestore db,DocumentReference ref,String tenant)throws Exception{
  db.runTransaction(tx->{var doc=tx.get(ref).get();if(!doc.exists()||!tenant.equals(doc.getString("tenantId"))||Set.of("RESOLVED","PAUSED","ARCHIVED").contains(doc.getString("status"))||doc.get("escalatedAt")!=null)return false;
   var now=Instant.now();boolean response=doc.get("acknowledgedAt")==null&&now.isAfter(Instant.parse(doc.getString("responseDeadline")));boolean resolution=now.isAfter(Instant.parse(doc.getString("resolutionDeadline")));if(!response&&!resolution)return false;
   tx.update(ref,Map.of("escalatedAt",now.toString(),"updatedAt",now.toString(),"version",((Number)doc.get("version")).longValue()+1));
   tx.set(db.collection("notificationHistory").document("sla_"+ref.getId()),Map.of("tenantId",tenant,"title","SLA oltre scadenza","message",String.valueOf(doc.get("name")),"target","BROADCAST","taskId",doc.get("taskId"),"type","SLA_ESCALATION","createdAt",now.toString(),"deliveryStatus","PENDING","delivered",0));
   tx.set(db.collection("auditLogs").document(),Map.of("tenantId",tenant,"actor","SYSTEM_PRO","action","SLA_ESCALATION","resource","sla","resourceId",ref.getId(),"result","SUCCESS","timestamp",now.toString()));return true;
  }).get();
 }
}
