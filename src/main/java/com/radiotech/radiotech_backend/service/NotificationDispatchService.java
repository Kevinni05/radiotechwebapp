package com.radiotech.radiotech_backend.service;

import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.model.Operator;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Validate all recipients before atomically recording an idempotent operation. */
@Service
public class NotificationDispatchService {
    private final OperatorService operators;
    private final NotificationService notifications;
    public NotificationDispatchService(OperatorService operators, NotificationService notifications) {
        this.operators=operators; this.notifications=notifications;
    }
    private static String digest(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    private static boolean active(Operator operator) {
        return operator != null && !"CUSTOMER".equalsIgnoreCase(operator.getRole())
            && operator.getFirebaseUid()!=null && !operator.getFirebaseUid().isBlank()
            && (operator.getStatus()==null || !Set.of("IN_ATTESA","INATTIVO","DISATTIVATO","SOSPESO","DISABLED").contains(operator.getStatus().toUpperCase(Locale.ROOT)));
    }
    public Map<String,Object> dispatch(Map<String,Object> request) throws Exception {
        String tenant=TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(),null);
        String actor=SecurityContextAccessor.currentUid();
        if(actor==null) throw new SecurityException("Identità richiesta.");
        String title=Objects.toString(request.get("title"),"").trim(),message=Objects.toString(request.get("message"),"").trim();
        if(title.isEmpty()||title.length()>200||message.isEmpty()||message.length()>4000) throw new IllegalArgumentException("Titolo e messaggio obbligatori (massimo 200 e 4000 caratteri).");
        String target=Objects.toString(request.get("target"),"ALL").toUpperCase(Locale.ROOT);
        if(!Set.of("ALL","SELECTED").contains(target)) throw new IllegalArgumentException("Destinazione non valida.");
        String operation=Objects.toString(request.get("operationId"),UUID.randomUUID().toString());
        if(!operation.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("Identità operazione non valida.");
        var selected=new TreeSet<String>();
        if("SELECTED".equals(target)) {
            if(!(request.get("recipients") instanceof List<?> values)||values.isEmpty()||values.size()>200) throw new IllegalArgumentException("Seleziona da 1 a 200 destinatari.");
            for(var value:values) {
                if(!(value instanceof String id)||!id.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("Destinatario non valido.");
                selected.add(id);
            }
        }
        String hash=digest(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(List.of(target,selected,title,message)));
        var db=FirestoreClient.getFirestore();
        var operationRef=db.collection("notificationOperations").document(digest(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(List.of(tenant,actor,operation))));
        var existing=operationRef.get().get();
        if(!existing.exists()) {
            var recipients=new ArrayList<Operator>();
            if("SELECTED".equals(target)) {
                for(String id:selected) {
                    var operator=operators.getById(id);
                    if(operator==null||!tenant.equals(operator.getTenantId())) throw new SecurityException("Destinatario non autorizzato.");
                    if(!active(operator)) throw new IllegalArgumentException("Uno dei destinatari non è un operatore attivo.");
                    recipients.add(operator);
                }
            } else {
                for(var operator:operators.getAllOperators()) if(tenant.equals(operator.getTenantId())&&active(operator)) recipients.add(operator);
            }
            if(recipients.size()>200) throw new IllegalArgumentException("Massimo 200 operatori per invio. Seleziona un gruppo.");
            var items=new LinkedHashMap<String,Map<String,Object>>();
            for(var operator:recipients) {
                String id="general_"+digest(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(List.of(tenant,actor,operation,operator.getId())));
                var item=NotificationService.inboxEntry(tenant,operator.getId(),title,message,Map.of("type","GENERAL_NOTIFICATION"));
                item.put("operationId",operation);item.put("createdBy",actor);item.put("requestHash",hash);
                item.put("recipientUid",operator.getFirebaseUid());
                items.put(id,item);
            }
            try { db.runTransaction(tx->{
                var current=tx.get(operationRef).get();
                if(current.exists()) {
                    if(!hash.equals(current.getString("requestHash"))) throw new IllegalArgumentException("Operazione già utilizzata con contenuto differente.");
                    return false;
                }
                for(var item:items.entrySet()) tx.create(db.collection("notificationHistory").document(item.getKey()),item.getValue());
                tx.create(operationRef,Map.of("tenantId",tenant,"createdBy",actor,"operationId",operation,"requestHash",hash,"notificationIds",List.copyOf(items.keySet()),"createdAt",Instant.now().toString()));
                return true;
            }).get(); } catch(java.util.concurrent.ExecutionException error) {
                for(Throwable cause=error;cause!=null;cause=cause.getCause()) if(cause instanceof IllegalArgumentException) throw new ResponseStatusException(HttpStatus.CONFLICT,"Operazione già utilizzata con contenuto differente.");
                throw error;
            }
            existing=operationRef.get().get();
        }
        if(!tenant.equals(existing.getString("tenantId"))||!actor.equals(existing.getString("createdBy"))) throw new SecurityException("Operazione non autorizzata.");
        if(!hash.equals(existing.getString("requestHash"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Operazione già utilizzata con contenuto differente.");
        @SuppressWarnings("unchecked") var ids=(List<String>)existing.get("notificationIds");
        long accepted=0,failed=0,noDevices=0,pending=0;
        for(String id:ids) {
            try { notifications.deliverRecorded(id); } catch(Exception ignored) { /* Durable outbox retains the operation. */ }
            var item=db.collection("notificationHistory").document(id).get().get();
            Long delivered=item.getLong("delivered"), failures=item.getLong("failedRecipients");
            accepted+=delivered==null?0:delivered;failed+=failures==null?0:failures;
            if("NO_TOKENS".equals(item.getString("deliveryStatus"))) noDevices++;
            else if(!"SENT".equals(item.getString("deliveryStatus"))) pending++;
        }
        String status=ids.isEmpty()?"NO_RECIPIENTS":noDevices==ids.size()?"NO_TOKENS":pending>0||noDevices>0?"PARTIAL":"SENT";
        return Map.of("success",true,"persisted",true,"operationId",operation,"notificationIds",ids,
            "deliveryStatus",status,"providerAccepted",accepted,"delivered",accepted,"failedDevices",failed,"recipientsWithoutDevices",noDevices);
    }
}
