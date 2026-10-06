package com.radiotech.radiotech_backend.service;

import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
public class ProService {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private ProWebhookService webhooks;
    private static final Set<Role> MANAGERS=Set.of(Role.ADMIN,Role.SUPER_ADMIN,Role.CHIEF_EXECUTIVE,Role.NETWORK_MANAGER);
    public record Change(long expectedVersion, String operationId, Map<String,Object> fields) {}
    public record Action(long expectedVersion, String operationId, String action, double quantity, String reason) {}
    private record Actor(String tenant, String uid, boolean manager, String customer, List<String> permissions) {}
    private Actor actor() {
        String tenant=TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(),null), uid=SecurityContextAccessor.currentUid();
        Role role=SecurityContextAccessor.currentRole();
        if (uid==null || (!MANAGERS.contains(role) && role!=Role.OPERATOR && role!=Role.CUSTOMER)) throw new SecurityException("Accesso non autorizzato.");
        var details=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getDetails();
        String customer=role==Role.CUSTOMER&&details instanceof FirebaseAuthenticationDetails d?d.getCustomerId():null;
        if(role==Role.CUSTOMER) id(customer);
        return new Actor(tenant,uid,MANAGERS.contains(role),customer,details instanceof FirebaseAuthenticationDetails d?d.getProPermissions():null);
    }
    private static boolean permitted(Actor a,String module,String operation){return a.permissions==null||a.permissions.contains(module+":"+operation);}
    private static void permission(Actor a,String module,String operation){if(!permitted(a,module,operation))throw new SecurityException("Permesso non assegnato per questa operazione.");}
    public void requireModulePermission(String module,String operation){permission(actor(),module,operation);}
    private static void id(String id) { if(id==null || !id.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("Identificativo non valido."); }
    private static CollectionReference collection(Firestore db,String module) { ProCatalog.module(module); return db.collection("pro_"+module); }
    private static void scope(Map<String,Object> data,Actor actor) { if(data==null || !actor.tenant.equals(data.get("tenantId"))) throw new SecurityException("Risorsa non autorizzata."); }
    private static void access(String module,Map<String,Object> data,Actor actor,boolean write) {
        scope(data,actor);
        if(actor.manager) return;
        if(actor.customer!=null) {
            if(!Set.of("sites","requests","documents").contains(module)||!actor.customer.equals(data.get("clientId"))) throw new SecurityException("Cliente non autorizzato.");
            if(module.equals("documents")&&(!"PUBLISHED".equals(data.get("status"))||!"CUSTOMER".equals(data.get("audience")))) throw new SecurityException("Documento non pubblicato per il cliente.");
            if(write&&(!module.equals("requests")||!actor.uid.equals(data.get("createdBy")))) throw new SecurityException("Modifica non autorizzata.");
            return;
        }
        if(!ProCatalog.module(module).operator() || (!Objects.equals(data.get("createdBy"),actor.uid) && !Objects.equals(data.get("recipientUid"),actor.uid) && !(module.equals("vanstock")&&Objects.equals(data.get("operatorFirebaseUid"),actor.uid)))) {
            if(!write && module.equals("documents") && "PUBLISHED".equals(data.get("status"))) return;
            throw new SecurityException("Risorsa non autorizzata.");
        }
        if(write && module.equals("documents")) throw new SecurityException("Pubblicazione riservata ai responsabili.");
    }
    public List<ProCatalog.Module> catalog() {
        var a=actor(); return ProCatalog.MODULES.stream().filter(m->permitted(a,m.id(),"READ")).filter(m->a.customer!=null?Set.of("sites","requests","documents").contains(m.id()):a.manager||m.operator())
            .map(m->a.customer!=null?new ProCatalog.Module(m.id(),m.title(),m.fields().stream().filter(f->!Set.of("operatorId","contractId","antennaId").contains(f.key())).toList(),List.of(),false,m.id().equals("requests")):new ProCatalog.Module(m.id(),m.title(),m.fields(),m.actions().stream().filter(action->permitted(a,m.id(),action)&&(a.manager||Set.of("SUBMIT","ACKNOWLEDGE","RESERVE","CONSUME","RETURN","REVOKE").contains(action))).toList(),m.operator(),m.writable()&&permitted(a,m.id(),"WRITE")&&(a.manager||!m.id().equals("documents")))).toList();
    }
    public Map<String,Object> list(String module) throws Exception {
        var a=actor(); var definition=ProCatalog.module(module);
        permission(a,module,"READ");
        if(module.equals("deliveries")){if(!a.manager)throw new SecurityException("Responsabile richiesto.");var results=new ArrayList<Map<String,Object>>();for(var d:FirestoreClient.getFirestore().collection("proWebhookOutbox").whereEqualTo("tenantId",a.tenant).limit(1001).get().get().getDocuments()){var r=new LinkedHashMap<>(d.getData());r.put("id",d.getId());r.put("name",r.get("event"));r.remove("payload");results.add(r);}return Map.of("records",results.stream().limit(1000).toList(),"partial",results.size()>1000);}
        if(Set.of("analytics","audit").contains(module)) { if(!a.manager) throw new SecurityException("Responsabile richiesto."); return module.equals("analytics")?analytics(a):audit(a); }
        if(a.customer!=null&&!Set.of("sites","requests","documents").contains(module)) throw new SecurityException("Sezione non autorizzata.");
        if(a.customer==null&&!a.manager&&!definition.operator()) throw new SecurityException("Sezione riservata ai responsabili.");
        var query=collection(FirestoreClient.getFirestore(),module).whereEqualTo("tenantId",a.tenant);
        var docs=query.limit(1001).get().get().getDocuments(); var records=new ArrayList<Map<String,Object>>();
        for(var doc:docs) { var data=new LinkedHashMap<>(doc.getData()); try { access(module,data,a,false); } catch(SecurityException e) { continue; } data.put("id",doc.getId()); decorate(module,data); if(a.customer!=null) { data.remove("operatorId"); data.remove("recipientUid"); data.remove("createdBy"); data.remove("updatedBy"); data.remove("contractId"); } records.add(data); }
        records.sort(Comparator.comparing(r->String.valueOf(r.get("updatedAt")),Comparator.reverseOrder()));
        return Map.of("records",records.stream().limit(1000).toList(),"partial",docs.size()>1000);
    }
    private Map<String,Object> audit(Actor a) throws Exception {
        var docs=FirestoreClient.getFirestore().collection("auditLogs").whereEqualTo("tenantId",a.tenant).limit(1001).get().get().getDocuments();
        var records=new ArrayList<Map<String,Object>>(); for(var doc:docs){var r=new LinkedHashMap<>(doc.getData());r.put("id",doc.getId());r.put("name",r.getOrDefault("action","Audit"));r.put("status",r.getOrDefault("result",""));records.add(r);}
        records.sort(Comparator.comparing(r->String.valueOf(r.get("timestamp")),Comparator.reverseOrder()));return Map.of("records",records.stream().limit(1000).toList(),"partial",docs.size()>1000);
    }
    private Map<String,Object> analytics(Actor a) throws Exception {
        var db=FirestoreClient.getFirestore();var taskDocs=db.collection("tasks").whereEqualTo("tenantId",a.tenant).limit(1001).get().get().getDocuments();
        var costDocs=collection(db,"costs").whereEqualTo("tenantId",a.tenant).limit(1001).get().get().getDocuments();
        var contractDocs=collection(db,"contracts").whereEqualTo("tenantId",a.tenant).limit(1001).get().get().getDocuments();
        boolean partial=taskDocs.size()>1000||costDocs.size()>1000||contractDocs.size()>1000;
        var now=Instant.now();long overdue=0,resolved=0;double totalHours=0,mttrHours=0;int measured=0;
        for(var doc:taskDocs){var data=doc.getData();String status=String.valueOf(data.get("status"));if(Set.of("APPROVED","CLOSED").contains(status)){resolved++;try{mttrHours+=Duration.between(Instant.parse(String.valueOf(data.get("createdAt"))),Instant.parse(String.valueOf(data.get("completedAt")))).toSeconds()/3600.0;measured++;}catch(Exception ignored){}}else if(!"CANCELLED".equals(status)){try{if(now.isAfter(Instant.parse(String.valueOf(data.get("dueAt")))))overdue++;}catch(Exception ignored){}}}
        double amount=0,budget=0;for(var doc:costDocs)if("APPROVED".equals(doc.getString("status"))){amount+=number(doc.get("amount"));totalHours+=number(doc.get("hours"));}for(var doc:contractDocs)if(!"ARCHIVED".equals(doc.getString("status")))budget+=number(doc.get("budget"));
        var metrics=new ArrayList<Map<String,Object>>();String notice=partial?"Campione limitato a 1001 documenti per sorgente; non usare per chiusura contabile.":"Dati del tenant; costi solo approvati. Non sostituisce la contabilità.";
        metrics.add(metric("overdue","Incarichi oltre scadenza",overdue,notice));metrics.add(metric("resolved","Incarichi approvati / chiusi",resolved,notice));metrics.add(metric("cost","Consuntivo approvato EUR",amount,notice));metrics.add(metric("budget","Budget contratti attivi EUR",budget,notice));metrics.add(metric("hours","Ore consuntivate approvate",totalHours,notice));metrics.add(metric("mttr","Tempo medio creazione-completamento (ore)",measured==0?"Storico insufficiente":mttrHours/measured,"Solo incarichi chiusi con entrambe le date: "+measured));
        return Map.of("records",metrics,"partial",partial);
    }
    private static Map<String,Object> metric(String id,String name,Object value,String notice){return Map.of("id",id,"name",name,"value",value,"notice",notice,"status","CALCULATED");}
    public Map<String,Object> taskPack(String taskId) throws Exception {
        id(taskId);var a=actor();if(a.customer!=null)throw new SecurityException("Operatore richiesto.");var db=FirestoreClient.getFirestore();var doc=db.collection("tasks").document(taskId).get().get();if(!doc.exists())throw new IllegalArgumentException("Incarico inesistente.");scope(doc.getData(),a);
        if(!a.manager&&!a.uid.equals(doc.get("operatorFirebaseUid")))throw new SecurityException("Incarico non assegnato.");
        var task=new LinkedHashMap<>(doc.getData());task.put("id",doc.getId());
        String siteId=task.get("metadata") instanceof Map<?,?> metadata&&metadata.get("siteId") instanceof String s?s:null;
        var site=new LinkedHashMap<String,Object>();if(siteId!=null){var s=reference(db,"sites",siteId).get().get();if(s.exists()){scope(s.getData(),a);site.putAll(s.getData());site.put("id",s.getId());}}
        var docs=new ArrayList<Map<String,Object>>();for(var d:collection(db,"documents").whereEqualTo("tenantId",a.tenant).limit(1000).get().get().getDocuments()){if(!"PUBLISHED".equals(d.getString("status")))continue;if((siteId!=null&&siteId.equals(d.getString("siteId")))||(task.get("antennaId")!=null&&task.get("antennaId").equals(d.get("antennaId")))){var r=new LinkedHashMap<>(d.getData());r.put("id",d.getId());docs.add(r);}}
        return Map.of("tenantId",a.tenant,"uid",a.uid,"task",task,"site",site,"documents",docs,"downloadedAt",Instant.now().toString(),"validUntil",Instant.now().plusSeconds(86400).toString());
    }
    private static void decorate(String module,Map<String,Object> data) {
        if(module.equals("vanstock"))data.put("summary",Map.of("Quantità a bordo",number(data.get("quantity")),"Riservata",number(data.get("reserved")),"Disponibile",number(data.get("quantity"))-number(data.get("reserved"))));
        if(!module.equals("sla")) return;
        data.put("summary",Map.of("Risposta entro",String.valueOf(data.get("responseDeadline")),"Risoluzione entro",String.valueOf(data.get("resolutionDeadline"))));
        var now=Instant.now(); boolean closed="RESOLVED".equals(data.get("status"));
        data.put("responseOverdue",!closed&&!data.containsKey("acknowledgedAt")&&now.isAfter(Instant.parse(data.get("responseDeadline").toString())));
        data.put("resolutionOverdue",!closed&&!"PAUSED".equals(data.get("status"))&&now.isAfter(Instant.parse(data.get("resolutionDeadline").toString())));
    }
    public List<Map<String,Object>> options(String target) throws Exception {
        var a=actor(); String coll;
        if(Set.of("operators","antennas","inventory","tasks").contains(target)) coll=target;
        else { ProCatalog.module(target); coll="pro_"+target; }
        if(a.customer!=null&&!Set.of("clients","sites").contains(target)) return List.of();
        if(a.customer==null&&!a.manager && !Set.of("tasks","inventory","operators").contains(target)) return List.of();
        if(ProCatalog.MODULES.stream().anyMatch(m->m.id().equals(target))&&!permitted(a,target,"READ"))return List.of();
        var result=new ArrayList<Map<String,Object>>();
        for(var doc:FirestoreClient.getFirestore().collection(coll).whereEqualTo("tenantId",a.tenant).limit(1000).get().get().getDocuments()) {
            var data=doc.getData();
            if(a.customer!=null&&!(target.equals("clients")?a.customer.equals(doc.getId()):a.customer.equals(data.get("clientId")))) continue;
            if(!a.manager && target.equals("operators")&&!a.uid.equals(data.get("firebaseUid"))) continue;
            if(!a.manager && target.equals("tasks")&&!a.uid.equals(data.get("operatorFirebaseUid"))) continue;
            if("ARCHIVED".equals(data.get("status"))||Boolean.FALSE.equals(data.get("active"))) continue;
            result.add(Map.of("id",doc.getId(),"name",String.valueOf(data.getOrDefault("name",data.getOrDefault("title",doc.getId())))));
        }
        return result;
    }
    private static DocumentReference reference(Firestore db,String target,String value) { id(value); return db.collection(Set.of("operators","antennas","inventory","tasks").contains(target)?target:"pro_"+target).document(value); }
    private static Map<String,Map<String,Object>> references(Transaction tx,Firestore db,ProCatalog.Module module,Map<String,Object> fields,Actor a) throws Exception {
        var refs=new HashMap<String,Map<String,Object>>();
        for(var field:module.fields()) if(field.type().startsWith("ref:")&&fields.containsKey(field.key())) {
            String target=field.type().substring(4); var doc=tx.get(reference(db,target,fields.get(field.key()).toString())).get();
            if(!doc.exists()) throw new IllegalArgumentException(field.label()+": risorsa inesistente."); scope(doc.getData(),a);
            if(a.customer!=null && !(target.equals("clients")?a.customer.equals(doc.getId()):target.equals("sites")&&a.customer.equals(doc.get("clientId")))) throw new SecurityException("Riferimento non autorizzato al cliente.");
            if("ARCHIVED".equals(doc.getString("status"))||Boolean.FALSE.equals(doc.get("active"))) throw new IllegalArgumentException("Risorsa archiviata.");
            if(!a.manager && target.equals("tasks")&&!a.uid.equals(doc.get("operatorFirebaseUid"))) throw new SecurityException("Incarico non assegnato.");
            if(!a.manager && target.equals("operators")&&!a.uid.equals(doc.get("firebaseUid"))) throw new SecurityException("Operatore non autorizzato.");
            refs.put(field.key(),doc.getData());
        }
        if(refs.containsKey("siteId")&&fields.containsKey("clientId")&&!fields.get("clientId").equals(refs.get("siteId").get("clientId"))) throw new IllegalArgumentException("La sede non appartiene al cliente.");
        if(refs.containsKey("contractId")&&fields.containsKey("clientId")&&!fields.get("clientId").equals(refs.get("contractId").get("clientId"))) throw new IllegalArgumentException("Contratto e cliente non corrispondono.");
        return refs;
    }
    private static ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT,"Dati aggiornati da un altro utente. Ricarica la sezione."); }
    private static long version(Map<String,Object> data) { return data.get("version") instanceof Number n?n.longValue():0; }
    private static double number(Object value) { return value instanceof Number n?n.doubleValue():0; }
    private static String fingerprint(Object value) throws Exception { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
    private static <T> T run(Firestore db,Transaction.Function<T> fn) throws Exception {
        try { return db.runTransaction(fn).get(); } catch(ExecutionException e) { Throwable cause=e.getCause(); while(cause.getCause()!=null && !(cause instanceof ResponseStatusException) && !(cause instanceof SecurityException) && !(cause instanceof IllegalArgumentException)) cause=cause.getCause(); if(cause instanceof RuntimeException r) throw r; throw e; }
    }
    public Map<String,Object> save(String module,String recordId,Change change) throws Exception {
        var a=actor(); var definition=ProCatalog.module(module);
        permission(a,module,"WRITE");
        if(Set.of("analytics","audit","orders","deliveries","devices").contains(module)) throw new SecurityException("Sezione di sola lettura.");
        if(a.customer!=null&&!module.equals("requests")) throw new SecurityException("Operazione non autorizzata.");
        if(a.customer==null&&!a.manager && (!definition.operator()||module.equals("documents"))) throw new SecurityException("Operazione riservata ai responsabili.");
        if(change==null||change.fields==null) throw new IllegalArgumentException("Campi obbligatori.");
        id(change.operationId); var fields=ProCatalog.validate(definition,change.fields);
        if(module.equals("dispatch")&&!Instant.parse(fields.get("endAt").toString()).isAfter(Instant.parse(fields.get("startAt").toString()))) throw new IllegalArgumentException("La fine deve seguire l'inizio.");
        if(a.customer!=null && (!a.customer.equals(fields.get("clientId"))||fields.keySet().stream().anyMatch(k->Set.of("operatorId","contractId","antennaId").contains(k)))) throw new SecurityException("Campi non autorizzati.");
        var db=FirestoreClient.getFirestore(); boolean create=recordId==null;
        if(create) recordId="n_"+fingerprint(a.tenant+":"+a.uid+":"+module+":"+change.operationId); id(recordId);
        var ref=collection(db,module).document(recordId); var op=db.collection("proOperations").document(fingerprint(a.tenant+":"+a.uid+":"+change.operationId));
        String fp=fingerprint(module+":"+recordId+":"+change.expectedVersion+":"+new TreeMap<>(fields)); var now=Instant.now().toString();
        return run(db,tx->{
            var prior=tx.get(op).get(); var doc=tx.get(ref).get();
            if(prior.exists()) { if(!fp.equals(prior.getString("fingerprint"))) throw conflict(); return (Map<String,Object>)prior.get("result"); }
            Map<String,Object> before=doc.exists()?new LinkedHashMap<>(doc.getData()):Map.of();
            if(doc.exists()) { access(module,before,a,true); if(create) throw conflict(); }
            else if(!create) throw new IllegalArgumentException("Risorsa inesistente.");
            if(version(before)!=change.expectedVersion) throw conflict();
            if(doc.exists()&&!Set.of("DRAFT","ACTIVE","PUBLISHED").contains(String.valueOf(before.get("status")))&&!(a.manager&&module.equals("requests")&&"ACKNOWLEDGED".equals(before.get("status")))) throw new IllegalArgumentException("Modifica non consentita nello stato corrente.");
            if(doc.exists()&&module.equals("vanstock")) throw new IllegalArgumentException("Usa i movimenti per modificare la dotazione.");
            if(doc.exists()&&module.equals("sla")) throw new IllegalArgumentException("Usa le azioni SLA per modificare la pratica senza azzerare le scadenze.");
            var related=references(tx,db,definition,fields,a);
            var integrations=tx.get(collection(db,"integrations").whereEqualTo("tenantId",a.tenant).whereEqualTo("deliveryEnabled",true).limit(100)).get().getDocuments();
            var after=new LinkedHashMap<>(fields); after.put("tenantId",a.tenant); after.put("createdBy",before.getOrDefault("createdBy",a.uid)); after.put("createdAt",before.getOrDefault("createdAt",now)); after.put("updatedAt",now); after.put("updatedBy",a.uid); after.put("version",version(before)+1);
            if(module.equals("documents")) after.put("clientId",related.get("siteId").get("clientId"));
            after.put("status",before.getOrDefault("status",Set.of("purchases","costs","documents").contains(module)?"DRAFT":"ACTIVE"));
            if(module.equals("integrations")) { after.put("deliveryEnabled",false); after.put("activationNotice","Richiede endpoint autorizzato e secret gestito nel deploy."); }
            if(module.equals("vanstock")) { if(!(related.get("operatorId").get("firebaseUid") instanceof String operatorUid)||operatorUid.isBlank())throw new IllegalArgumentException("Operatore senza account."); after.put("operatorFirebaseUid",operatorUid); after.put("quantity",0L); after.put("reserved",0L); }
            if(module.equals("sla")) { var contract=related.get("contractId"); var start=Instant.now(); after.put("responseDeadline",start.plusSeconds((long)number(contract.get("responseMinutes"))*60).toString()); after.put("resolutionDeadline",start.plusSeconds((long)number(contract.get("resolutionMinutes"))*60).toString()); }
            if(module.equals("documents")&&doc.exists()) { tx.set(ref.collection("revisions").document(String.valueOf(version(before))),before); after.put("status","DRAFT"); }
            tx.set(ref,after); var result=new LinkedHashMap<>(after); result.put("id",ref.getId());
            emit(tx,db,a,integrations,module,ref.getId(),create?"CREATE":"UPDATE",after,now);
            tx.set(op,Map.of("tenantId",a.tenant,"uid",a.uid,"fingerprint",fp,"result",result,"createdAt",now));
            tx.set(db.collection("auditLogs").document(),Map.of("tenantId",a.tenant,"actor",a.uid,"action",create?"PRO_CREATE":"PRO_UPDATE","resource",module,"resourceId",ref.getId(),"result","SUCCESS","before",before,"after",after,"timestamp",now));
            return result;
        });
    }
    public Map<String,Object> action(String module,String recordId,Action command) throws Exception {
        var a=actor(); var definition=ProCatalog.module(module); id(recordId);
        if(a.customer!=null) throw new SecurityException("Azione riservata ai responsabili.");
        if(command==null) throw new IllegalArgumentException("Azione obbligatoria."); id(command.operationId);
        permission(a,module,command.action);
        if(!definition.actions().contains(command.action)) throw new IllegalArgumentException("Azione non prevista.");
        if(!Double.isFinite(command.quantity)||command.quantity<0||command.quantity>1_000_000||command.quantity!=Math.rint(command.quantity)) throw new IllegalArgumentException("Quantità non valida.");
        if(command.reason!=null && command.reason.length()>2000) throw new IllegalArgumentException("Motivo troppo lungo.");
        if(!a.manager&&!Set.of("SUBMIT","ACKNOWLEDGE","RESERVE","CONSUME","RETURN").contains(command.action)) throw new SecurityException("Azione riservata ai responsabili.");
        var db=FirestoreClient.getFirestore(); var ref=collection(db,module).document(recordId); String fp=fingerprint(module+":"+recordId+":"+command); var op=db.collection("proOperations").document(fingerprint(a.tenant+":"+a.uid+":"+command.operationId)); String now=Instant.now().toString();
        String validatedEndpoint;
        if(module.equals("integrations")&&command.action.equals("ENABLE")){var snapshot=ref.get().get();if(!snapshot.exists())throw new IllegalArgumentException("Integrazione inesistente.");scope(snapshot.getData(),a);if(webhooks==null)throw new IllegalArgumentException("Webhook non configurati.");validatedEndpoint=snapshot.getString("endpoint");webhooks.validateEndpoint(validatedEndpoint);}else validatedEndpoint=null;
        return run(db,tx->{
            var prior=tx.get(op).get(); var doc=tx.get(ref).get();
            if(prior.exists()) { if(!fp.equals(prior.getString("fingerprint"))) throw conflict(); return (Map<String,Object>)prior.get("result"); }
            if(!doc.exists()) throw new IllegalArgumentException("Risorsa inesistente.");
            var before=new LinkedHashMap<>(doc.getData()); access(module,before,a,true);
            if(version(before)!=command.expectedVersion) throw conflict();
            var related=references(tx,db,definition,before,a);
            var integrations=tx.get(collection(db,"integrations").whereEqualTo("tenantId",a.tenant).whereEqualTo("deliveryEnabled",true).limit(100)).get().getDocuments();
            var after=new LinkedHashMap<>(before); var writes=new LinkedHashMap<DocumentReference,Map<String,Object>>();
            DocumentSnapshot order=module.equals("purchases")&&command.action.equals("RECEIVE")?tx.get(collection(db,"orders").document(recordId)).get():null;
            String status=String.valueOf(before.get("status"));
            switch(command.action) {
                case "ENABLE" -> {requireStatus(status,"ACTIVE");if(!Objects.equals(validatedEndpoint,before.get("endpoint")))throw conflict();after.put("deliveryEnabled",true);}
                case "DISABLE" -> {after.put("deliveryEnabled",false);}
                case "ARCHIVE" -> { if(status.equals("ARCHIVED") || (module.equals("vanstock") && number(before.get("quantity"))>0)) throw new IllegalArgumentException("Archiviazione non consentita."); if(module.equals("dispatch")){var task=new LinkedHashMap<>(related.get("taskId"));if(task.get("metadata") instanceof Map<?,?> map&&recordId.equals(map.get("dispatchId"))){var metadata=new LinkedHashMap<String,Object>((Map<String,Object>)map);metadata.remove("dispatchId");metadata.remove("scheduledStartAt");metadata.remove("scheduledEndAt");task.put("metadata",metadata);task.put("updatedAt",now);writes.put(reference(db,"tasks",before.get("taskId").toString()),task);}}after.put("status","ARCHIVED"); }
                case "SUBMIT" -> { requireStatus(status,"DRAFT"); after.put("status","SUBMITTED"); }
                case "APPROVE","REJECT" -> { requireStatus(status,"SUBMITTED"); if(a.uid.equals(before.get("createdBy"))) throw new IllegalArgumentException("Serve l'approvazione di un altro responsabile."); after.put("status",command.action.equals("APPROVE")?"APPROVED":"REJECTED"); after.put("approvedBy",a.uid); if(module.equals("purchases")&&command.action.equals("APPROVE")){var issued=new LinkedHashMap<>(after);issued.put("purchaseId",ref.getId());issued.put("status","ORDERED");issued.put("updatedAt",now);writes.put(collection(db,"orders").document(recordId),issued);} }
                case "PUBLISH" -> { requireStatus(status,"DRAFT"); after.put("status","PUBLISHED"); }
                case "RECEIVE" -> {
                    if(order==null||!order.exists()) throw new IllegalArgumentException("Ordine non disponibile."); scope(order.getData(),a);var deliveredOrder=new LinkedHashMap<>(order.getData());deliveredOrder.put("status","RECEIVED");deliveredOrder.put("updatedAt",now);writes.put(order.getReference(),deliveredOrder);
                    requireStatus(status,"APPROVED"); var item=related.get("inventoryId");
                    var update=new LinkedHashMap<>(item); update.put("quantity",number(item.get("quantity"))+number(before.get("quantity"))); update.put("updatedAt",now);
                    writes.put(reference(db,"inventory",before.get("inventoryId").toString()),update); movement(db,writes,a,before,"RECEIPT",number(before.get("quantity")),now); after.put("status","RECEIVED");
                }
                case "LOAD","RESERVE","CONSUME","RETURN" -> {
                    requireStatus(status,"ACTIVE"); if(command.quantity<=0) throw new IllegalArgumentException("Quantità obbligatoria.");
                    double qty=number(before.get("quantity")), reserved=number(before.get("reserved")); var item=related.get("inventoryId"); double central=number(item.get("quantity"));
                    switch(command.action) {
                        case "LOAD" -> { if(central<command.quantity) throw new IllegalArgumentException("Giacenza centrale insufficiente."); qty+=command.quantity; central-=command.quantity; }
                        case "RESERVE" -> { if(qty-reserved<command.quantity) throw new IllegalArgumentException("Dotazione libera insufficiente."); reserved+=command.quantity; }
                        case "CONSUME" -> { if(qty<command.quantity) throw new IllegalArgumentException("Dotazione insufficiente."); qty-=command.quantity; reserved=Math.max(0,reserved-command.quantity); }
                        case "RETURN" -> { if(qty-reserved<command.quantity) throw new IllegalArgumentException("Quantità libera insufficiente."); qty-=command.quantity; central+=command.quantity; }
                    }
                    after.put("quantity",qty); after.put("reserved",reserved); var update=new LinkedHashMap<>(item); update.put("quantity",central); update.put("updatedAt",now); writes.put(reference(db,"inventory",before.get("inventoryId").toString()),update); movement(db,writes,a,before,command.action,command.quantity,now);
                }
                case "GENERATE","ASSIGN" -> {
                    if(module.equals("dispatch")) {
                        requireStatus(status,"ACTIVE");var operator=related.get("operatorId");String operatorId=before.get("operatorId").toString();String operatorUid=String.valueOf(operator.get("firebaseUid"));if(operatorUid.equals("null"))throw new IllegalArgumentException("Operatore senza account.");
                        var capacity=db.collection("proDispatchLocks").document(fingerprint(a.tenant+":"+operatorId));var lock=tx.get(capacity).get();
                        var shift=tx.get(db.collection("workforceShifts").document(fingerprint(a.tenant+":"+operatorUid))).get();
                        if(shift.exists()&&!"ACTIVE".equals(shift.getString("status"))&&!"YES".equals(before.get("availabilityOverride")))throw new IllegalArgumentException("Operatore fuori turno o in pausa. Serve un'autorizzazione esplicita.");
                        var bookings=tx.get(collection(db,"dispatch").whereEqualTo("tenantId",a.tenant).whereEqualTo("status","ASSIGNED").limit(1001)).get().getDocuments();if(bookings.size()>1000)throw new IllegalArgumentException("Limite di pianificazione raggiunto.");
                        var start=Instant.parse(before.get("startAt").toString());var end=Instant.parse(before.get("endAt").toString());
                        for(var booking:bookings) if(!booking.getId().equals(recordId)&&"ASSIGNED".equals(booking.getString("status"))&&operatorId.equals(booking.get("operatorId"))&&start.isBefore(Instant.parse(booking.getString("endAt")))&&end.isAfter(Instant.parse(booking.getString("startAt")))) throw new IllegalArgumentException("Operatore già impegnato in questo intervallo.");
                        for(String skill:String.valueOf(before.getOrDefault("requiredSkills","")).split(","))if(!skill.isBlank()){String code=skill.trim();id(code);var cert=tx.get(reference(db,"operators",operatorId).collection("skills").document(code)).get();if(!cert.exists()||!a.tenant.equals(cert.getString("tenantId"))||!Boolean.TRUE.equals(cert.get("authorized"))||LocalDate.parse(cert.getString("expiration")).isBefore(start.atZone(ZoneOffset.UTC).toLocalDate()))throw new IllegalArgumentException("Competenza mancante o scaduta: "+code);}
                        var task=new LinkedHashMap<>(related.get("taskId"));if(!Set.of("CREATED","ASSIGNED").contains(task.get("status")))throw new IllegalArgumentException("Incarico già avviato.");var metadata=task.get("metadata") instanceof Map<?,?> map?new LinkedHashMap<String,Object>((Map<String,Object>)map):new LinkedHashMap<String,Object>();
                        if(metadata.get("dispatchId")!=null&&!recordId.equals(metadata.get("dispatchId")))throw new IllegalArgumentException("Incarico già pianificato. Archiviare prima la prenotazione precedente.");
                        metadata.put("dispatchId",recordId);metadata.put("scheduledStartAt",start.toString());metadata.put("scheduledEndAt",end.toString());task.put("metadata",metadata);task.put("operatorId",operatorId);task.put("operatorFirebaseUid",operatorUid);task.put("operatorName",operator.getOrDefault("name","Operatore"));task.put("status","ASSIGNED");task.put("updatedAt",now);writes.put(reference(db,"tasks",before.get("taskId").toString()),task);writes.put(capacity,Map.of("tenantId",a.tenant,"version",lock.exists()?version(lock.getData())+1:1L,"updatedAt",now));after.put("status","ASSIGNED");break;
                    }
                    if(before.get("operatorId")==null||before.get("antennaId")==null) throw new IllegalArgumentException("Prima scegliere operatore e antenna modificando la richiesta.");
                    requireStatus(status,command.action.equals("GENERATE")?"ACTIVE":"ACKNOWLEDGED");
                    String due=module.equals("plans")?before.get("nextDueAt").toString():Instant.now().plusSeconds(86400).toString();
                    if(module.equals("plans")&&Instant.parse(due).isAfter(Instant.now())) throw new IllegalArgumentException("Il piano non è ancora in scadenza.");
                    var taskRef=db.collection("tasks").document("pro_"+ref.getId()+"_"+version(before)); var existing=tx.get(taskRef).get(); if(existing.exists()) throw conflict();
                    var operator=related.get("operatorId"); if(operator.get("firebaseUid")==null) throw new IllegalArgumentException("Operatore senza account abilitato.");
                    var task=new LinkedHashMap<String,Object>(); task.put("tenantId",a.tenant); task.put("id",taskRef.getId()); task.put("title",before.get("name")); task.put("description",before.getOrDefault("description",before.getOrDefault("checklist",""))); task.put("operatorId",before.get("operatorId")); task.put("operatorFirebaseUid",operator.get("firebaseUid")); task.put("operatorName",operator.getOrDefault("name","Operatore")); task.put("antennaId",before.get("antennaId")); task.put("status","ASSIGNED"); task.put("priority",before.getOrDefault("priority","MEDIUM")); task.put("dueAt",due); task.put("createdAt",now); task.put("updatedAt",now); task.put("createdBy",a.uid); task.put("metadata",Map.of("proSource",ref.getId(),"siteId",before.get("siteId"),"checklist",before.getOrDefault("checklist",""))); writes.put(taskRef,task); after.put("lastTaskId",taskRef.getId());
                    if(module.equals("plans")) after.put("nextDueAt",Instant.parse(due).plusSeconds((long)number(before.get("intervalDays"))*86400).toString()); else after.put("status","ASSIGNED");
                }
                case "ACKNOWLEDGE" -> { requireStatus(status,"ACTIVE"); if(module.equals("handovers")&&!a.uid.equals(before.get("recipientUid"))) throw new SecurityException("Solo il destinatario può confermare la consegna."); after.put("status","ACKNOWLEDGED"); after.put("acknowledgedAt",now); after.put("acknowledgedBy",a.uid); }
                case "PAUSE" -> { if(!Set.of("ACTIVE","ACKNOWLEDGED").contains(status)||command.reason==null||command.reason.isBlank()) throw new IllegalArgumentException("Indicare il motivo della sospensione."); after.put("previousStatus",status); after.put("status","PAUSED"); after.put("pausedAt",now); after.put("pauseReason",command.reason); }
                case "RESUME" -> { requireStatus(status,"PAUSED"); long seconds=Duration.between(Instant.parse(before.get("pausedAt").toString()),Instant.parse(now)).getSeconds(); after.put("resolutionDeadline",Instant.parse(before.get("resolutionDeadline").toString()).plusSeconds(seconds).toString()); after.put("status",before.get("previousStatus")); after.remove("pausedAt"); }
                case "RESOLVE" -> { if(!Set.of("ACKNOWLEDGED","ASSIGNED").contains(status)) throw new IllegalArgumentException("Prima prendere in carico la richiesta."); after.put("status","RESOLVED"); after.put("resolvedAt",now); }
                default -> throw new IllegalArgumentException("Azione non valida.");
            }
            after.put("version",version(before)+1); after.put("updatedAt",now); after.put("updatedBy",a.uid);
            for(var write:new ArrayList<>(writes.entrySet())) if(write.getKey().getParent().getId().equals("tasks")) {
                var task=write.getValue();var notification=new LinkedHashMap<String,Object>();notification.put("tenantId",a.tenant);notification.put("target",task.get("operatorId"));notification.put("title","Incarico assegnato");notification.put("message",task.get("title"));notification.put("taskId",write.getKey().getId());notification.put("type","TASK_ASSIGNED");notification.put("createdAt",now);notification.put("deliveryStatus","PENDING");notification.put("delivered",0L);
                if(!command.action.equals("ARCHIVE"))writes.put(db.collection("notificationHistory").document("pro_"+fingerprint(fp)),notification);
            }
            for(var write:writes.entrySet()) tx.set(write.getKey(),write.getValue()); tx.set(ref,after);
            emit(tx,db,a,integrations,module,ref.getId(),command.action,after,now);
            var result=new LinkedHashMap<>(after); result.put("id",ref.getId()); tx.set(op,Map.of("tenantId",a.tenant,"uid",a.uid,"fingerprint",fp,"result",result,"createdAt",now));
            tx.set(db.collection("auditLogs").document(),Map.of("tenantId",a.tenant,"actor",a.uid,"action","PRO_"+command.action,"resource",module,"resourceId",ref.getId(),"result","SUCCESS","before",before,"after",after,"timestamp",now)); return result;
        });
    }
    private static void movement(Firestore db,Map<DocumentReference,Map<String,Object>> writes,Actor a,Map<String,Object> record,String type,double qty,String now) { writes.put(db.collection("inventoryMovements").document(),Map.of("tenantId",a.tenant,"inventoryId",record.get("inventoryId"),"type",type,"quantity",qty,"actorUid",a.uid,"reference",record.get("name"),"createdAt",now)); }
    private static void requireStatus(String actual,String expected) { if(!actual.equals(expected)) throw new IllegalArgumentException("Azione non consentita nello stato "+actual+"."); }
    private static void emit(Transaction tx,Firestore db,Actor a,List<QueryDocumentSnapshot> integrations,String module,String resourceId,String action,Map<String,Object> after,String now)throws Exception {
        String event=module+"."+action;
        for(var integration:integrations){String events=integration.getString("events");if(events==null||Arrays.stream(events.split(",")).map(String::trim).noneMatch(e->e.equals("*")||e.equals(event)))continue;String eventId=fingerprint(a.tenant+":"+module+":"+resourceId+":"+after.get("version")+":"+integration.getId());
            tx.set(db.collection("proWebhookOutbox").document(eventId),Map.of("tenantId",a.tenant,"integrationId",integration.getId(),"event",event,"status","PENDING","attempts",0L,"createdAt",now,"payload",Map.of("id",eventId,"tenantId",a.tenant,"event",event,"resourceId",resourceId,"record",after,"createdAt",now)));
        }
    }
}
