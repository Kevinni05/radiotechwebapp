package com.radiotech.radiotech_backend.dossier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.model.Antenna;
import com.radiotech.radiotech_backend.security.*;
import com.radiotech.radiotech_backend.service.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import static com.radiotech.radiotech_backend.dossier.DossierRules.*;

@Service
public class DossierService {
    private final AntennaService antennas; private final LocalAttachmentService files; private final OperatorService operators; private final ObjectMapper json;
    @Value("${radiotech.public-base-url:http://localhost:8080}") private String origin;
    public DossierService(AntennaService antennas,LocalAttachmentService files,OperatorService operators,ObjectMapper json){this.antennas=antennas;this.files=files;this.operators=operators;this.json=json;}
    public record Change(long expectedVersion,String operationId,String section,Map<String,Object> data) {}
    public record Review(long expectedVersion,String operationId,String proposalId,boolean approve,String reason) {}
    public record Share(List<String> sections,int hours) {}
    public record PublicRead(String token,String fileId) {}
    public record SessionCreate(String peerAntennaId,String operatorAId,String operatorBId,String title,String operationId) {}
    public record Trial(long expectedVersion,String operationId,String action,String trialId,String side,String label,Map<String,Object> settings,String metric,String unit,Double value,String notes) {}
    static String tenant(){return TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(),null);}
    static String uid(){String s=SecurityContextAccessor.currentUid();if(s==null||s.isBlank())throw new SecurityException("Sessione richiesta.");return s;}
    public static boolean manager(){return Arrays.asList(Role.MANAGERS).contains(SecurityContextAccessor.currentRole().name());}
    static void readAccess(){if(!TenantAccessPolicy.canAccess(SecurityContextAccessor.currentRole(),Permission.ASSET_READ)||SecurityContextAccessor.currentRole()==Role.CUSTOMER)throw new SecurityException("Fascicolo non autorizzato.");tenant();uid();}
    static void writeAccess(boolean management){readAccess();if(management&&!manager()||SecurityContextAccessor.currentRole()==Role.VIEWER)throw new SecurityException("Operazione riservata al responsabile.");}
    static String digest(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    static Firestore db(){return FirestoreClient.getFirestore();}
    static DocumentReference ref(String tenant,String antenna){return db().collection("antennaDossiers").document(digest(tenant+":"+antenna));}
    static void owned(DocumentSnapshot s,String t){if(s.exists()&&!t.equals(s.getString("tenantId")))throw new SecurityException("Risorsa non autorizzata.");}
    static <T>T await(com.google.api.core.ApiFuture<T> f)throws Exception{try{return f.get();}catch(ExecutionException e){if(e.getCause() instanceof Exception x)throw x;throw e;}}
    static RuntimeException conflict(){return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Dati aggiornati da un altro utente. Ricarica prima di salvare.");}
    private void bounded(Map<String,Object> value)throws Exception{if(json.writeValueAsBytes(value).length>750000)throw bad("Fascicolo troppo grande: limite 750 KB di dati strutturati. Gli allegati sono separati.");}
    private Antenna asset(String id)throws Exception{readAccess();return antennas.getById(id(id));}
    public Map<String,Object> get(String id)throws Exception{
        Antenna a=asset(id);String t=tenant();var doc=ref(t,a.getId()).get().get();owned(doc,t);var out=doc.exists()?new LinkedHashMap<>(doc.getData()):empty();
        out.remove("tenantId");out.put("antennaId",a.getId());out.put("name",Objects.toString(a.getName(),a.getId()));out.put("canManage",manager());
        var identity=new LinkedHashMap<String,Object>();for(var e:Map.of("code",Objects.toString(a.getCode(),""),"status",Objects.toString(a.getStatus(),""),"site",Objects.toString(a.getSite(),""),"model",Objects.toString(a.getModel(),""),"serial",Objects.toString(a.getSerialNumber(),""),"manufacturer",Objects.toString(a.getManufacturer(),"")).entrySet())identity.put(e.getKey(),e.getValue());
        identity.put("specs",a.getSpecs()==null?Map.of():json.convertValue(a.getSpecs(),Map.class));if(a.getLat()!=null)identity.put("lat",a.getLat());if(a.getLng()!=null)identity.put("lng",a.getLng());out.put("identity",identity);
        var history=new ArrayList<Map<String,Object>>();boolean partial=false;
        for(String collection:List.of("tasks","maintenanceReports")){
            var docs=db().collection(collection).whereEqualTo("antennaId",a.getId()).limit(201).get().get().getDocuments();partial|=docs.size()>200;
            for(var s:docs.stream().limit(200).toList())if(t.equals(s.getString("tenantId"))){var row=new LinkedHashMap<String,Object>();row.put("id",s.getId());row.put("type",collection.equals("tasks")?"TASK":"REPORT");for(String k:List.of("title","status","summary","description","createdAt","submittedAt","operatorName"))if(s.contains(k))row.put(k,text(s.get(k),4000));history.add(row);}
        }
        history.sort(Comparator.comparing((Map<String,Object> r)->Objects.toString(r.getOrDefault("submittedAt",r.getOrDefault("createdAt","")),"")).reversed());out.put("history",history);out.put("historyPartial",partial);out.put("fetchedAt",Instant.now().toString());out.put("tenantId",t);out.put("uid",uid());out.put("validUntil",Instant.now().plus(Duration.ofHours(24)).toString());
        var dependencies=rows(out,"dependencies");for(var edge:dependencies){try{var peer=antennas.getById(edge.get("antennaId").toString());edge.put("name",Objects.toString(peer.getName(),peer.getId()));edge.put("status",Objects.toString(peer.getStatus(),""));}catch(IllegalArgumentException e){edge.put("name","Impianto non più disponibile");edge.put("status","UNKNOWN");}}out.put("dependencies",dependencies);
        return out;
    }
    public Map<String,Object> change(String antennaId,Change change)throws Exception{
        if(change==null||change.data()==null)throw bad("Dati obbligatori.");String section=text(change.section(),30);writeAccess(Set.of("access","configuration","dependencies","components").contains(section));Antenna a=asset(antennaId);String t=tenant(),actor=uid(),op=id(change.operationId());
        Map<String,Object> canonical;
        if(section.equals("proposals")){
            String target=required(change.data(),"target",20);if(!Set.of("access","configuration").contains(target))throw bad("Correzione non ammessa.");String field=required(change.data(),"field",80);if(target.equals("access")&&!Set.of("address","contact","hours","parking","keyInstructions","route","restrictions").contains(field)||field.contains("."))throw bad("Campo non valido.");
            canonical=new LinkedHashMap<>(Map.of("target",target,"field",field,"value",required(change.data(),"value",2000),"reason",required(change.data(),"reason",2000),"status","PENDING"));
        }else canonical=canonical(section,change.data());
        if(section.equals("dependencies")){String peer=canonical.get("antennaId").toString();if(peer.equals(a.getId()))throw bad("Un impianto non può dipendere da sé stesso.");antennas.getById(peer);}
        if(section.equals("documents")||section.equals("photos")){
            var attachment=files.read(canonical.get("fileId").toString(),t,actor,manager());String name=text(attachment.get("name"),240);canonical.put("fileName",name);
            if(section.equals("photos")&&!name.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|webp)$"))throw bad("Usa una fotografia PNG, JPEG o WebP.");
        }
        String hash=digest(json.writeValueAsString(change));var root=ref(t,a.getId());var receipt=root.collection("operations").document(op);var antenna=db().collection("antennas").document(a.getId());
        return await(db().runTransaction(tx->{
            var live=tx.get(antenna).get();if(!live.exists()||!t.equals(live.getString("tenantId")))throw new SecurityException("Impianto non autorizzato.");
            var prior=tx.get(receipt).get();if(prior.exists()){if(!hash.equals(prior.getString("hash")))throw bad("Identificativo operazione già usato.");return Map.<String,Object>of("version",prior.get("version"));}
            var snapshot=tx.get(root).get();owned(snapshot,t);var state=snapshot.exists()?new LinkedHashMap<>(snapshot.getData()):empty();if(version(state.get("version"))!=change.expectedVersion())throw conflict();
            String now=Instant.now().toString();var row=new LinkedHashMap<>(canonical);row.put("id",op);row.put("actor",actor);row.put("createdAt",now);
            if(section.equals("access"))state.put(section,canonical);else{
                var list=rows(state,section);int max=section.equals("measurements")?500:100;if(list.size()>=max)throw bad("Limite della sezione raggiunto: "+max+" elementi. Nessun dato eliminato.");
                if(section.equals("proposals")){var current=proposalValues(state,row.get("target").toString());row.put("before",Objects.toString(current.get(row.get("field")),""));}
                if(section.equals("dependencies")&&list.stream().anyMatch(e->e.get("antennaId").equals(row.get("antennaId"))))throw bad("Collegamento già presente.");list.add(row);state.put(section,list);
            }
            long next=change.expectedVersion()+1;state.put("tenantId",t);state.put("antennaId",a.getId());state.put("version",next);state.put("updatedAt",now);bounded(state);
            tx.set(root,state);tx.create(receipt,Map.of("hash",hash,"version",next));tx.create(root.collection("audit").document(op),Map.of("section",section,"actor",actor,"createdAt",now,"version",next,"data",canonical));return Map.<String,Object>of("version",next);
        }));
    }
    static Map<String,Object> proposalValues(Map<String,Object> state,String target){if(target.equals("access"))return map(state.get("access"));var configs=rows(state,"configuration");return configs.isEmpty()?new LinkedHashMap<>():map(configs.getLast().get("values"));}
    public Map<String,Object> review(String antennaId,Review review)throws Exception{
        writeAccess(true);var a=asset(antennaId);String t=tenant(),actor=uid(),op=id(review.operationId()),reason=text(review.reason(),2000);if(reason.isBlank())throw bad("Motivazione obbligatoria.");var root=ref(t,a.getId());var receipt=root.collection("operations").document(op);String hash=digest(json.writeValueAsString(review));
        return await(db().runTransaction(tx->{var prior=tx.get(receipt).get();if(prior.exists()){if(!hash.equals(prior.getString("hash")))throw bad("Operazione già usata.");return Map.<String,Object>of("version",prior.get("version"));}var doc=tx.get(root).get();owned(doc,t);if(!doc.exists()||version(doc.get("version"))!=review.expectedVersion())throw conflict();var state=new LinkedHashMap<>(doc.getData());var proposals=rows(state,"proposals");var p=proposals.stream().filter(r->id(review.proposalId()).equals(r.get("id"))).findFirst().orElseThrow(()->bad("Proposta inesistente."));if(!"PENDING".equals(p.get("status")))throw conflict();
            if(review.approve()){var values=proposalValues(state,p.get("target").toString());if(!Objects.toString(values.get(p.get("field")),"").equals(p.get("before")))throw conflict();values.put(p.get("field").toString(),p.get("value"));if(p.get("target").equals("access"))state.put("access",canonical("access",values));else{var configs=rows(state,"configuration");if(configs.size()>=100)throw bad("Limite revisioni raggiunto.");configs.add(Map.of("id",op,"label","Correzione approvata","values",strings(values,60),"reason",reason,"actor",actor,"createdAt",Instant.now().toString()));state.put("configuration",configs);}}
            p.put("status",review.approve()?"APPROVED":"REJECTED");p.put("reviewedBy",actor);p.put("reviewReason",reason);p.put("reviewedAt",Instant.now().toString());state.put("proposals",proposals);state.put("version",review.expectedVersion()+1);bounded(state);tx.set(root,state);tx.create(receipt,Map.of("hash",hash,"version",review.expectedVersion()+1));tx.create(root.collection("audit").document(op),Map.of("action","REVIEW","actor",actor,"reason",reason,"proposalId",p.get("id"),"approved",review.approve(),"createdAt",Instant.now().toString()));return Map.<String,Object>of("version",review.expectedVersion()+1);
        }));
    }
    public Map<String,Object> createShare(String antennaId,Share request)throws Exception{
        writeAccess(true);if(request.hours()<1||request.hours()>720)throw bad("Scadenza consentita: da 1 ora a 30 giorni.");var snapshot=publicSnapshot(get(antennaId),request.sections());bounded(snapshot);byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),key=digest(token);String until=Instant.now().plus(Duration.ofHours(request.hours())).toString();
        var record=new LinkedHashMap<String,Object>();record.put("tenantId",tenant());record.put("antennaId",antennaId);record.put("snapshot",snapshot);record.put("sections",request.sections());record.put("expiresAt",until);record.put("revoked",false);record.put("createdAt",Instant.now().toString());record.put("createdBy",uid());record.put("accessCount",0L);db().collection("antennaDossierShares").document(key).create(record).get();
        return Map.of("id",key,"token",token,"url",origin.replaceAll("/+$","")+"/assets/dossier-share.html#"+token,"expiresAt",until);
    }
    public List<Map<String,Object>> shares(String antennaId)throws Exception{writeAccess(true);asset(antennaId);var result=new ArrayList<Map<String,Object>>();for(var d:db().collection("antennaDossierShares").whereEqualTo("antennaId",antennaId).limit(101).get().get().getDocuments())if(tenant().equals(d.getString("tenantId"))){var row=new LinkedHashMap<>(d.getData());row.remove("snapshot");row.put("id",d.getId());result.add(row);}return result;}
    public void revoke(String antennaId,String shareId)throws Exception{writeAccess(true);asset(antennaId);if(!shareId.matches("[a-f0-9]{64}"))throw bad("Link non valido.");var ref=db().collection("antennaDossierShares").document(shareId);String t=tenant(),actor=uid();await(db().runTransaction(tx->{var s=tx.get(ref).get();owned(s,t);if(!s.exists()||!antennaId.equals(s.getString("antennaId")))throw new SecurityException("Link non autorizzato.");tx.update(ref,Map.of("revoked",true,"revokedBy",actor,"revokedAt",Instant.now().toString()));return true;}));}
    public Map<String,Object> publicRead(PublicRead request)throws Exception{
        if(request==null||request.token()==null||!request.token().matches("[A-Za-z0-9_-]{43}"))throw new SecurityException("Link non disponibile o scaduto.");var reference=db().collection("antennaDossierShares").document(digest(request.token()));String event=UUID.randomUUID().toString();
        var record=await(db().runTransaction(tx->{var doc=tx.get(reference).get();if(!doc.exists()||Boolean.TRUE.equals(doc.getBoolean("revoked"))||!Instant.parse(doc.getString("expiresAt")).isAfter(Instant.now()))throw new SecurityException("Link non disponibile o scaduto.");tx.update(reference,Map.of("accessCount",version(doc.get("accessCount"))+1,"lastAccessAt",Instant.now().toString()));tx.create(reference.collection("accesses").document(event),Map.of("at",Instant.now().toString(),"type",request.fileId()==null?"VIEW":"FILE"));return new LinkedHashMap<>(doc.getData());}));
        var snapshot=map(record.get("snapshot"));if(request.fileId()==null)return Map.of("snapshot",snapshot,"expiresAt",record.get("expiresAt"));
        boolean allowed=false;for(String key:List.of("documents","photos"))if(rows(snapshot,key).stream().anyMatch(r->request.fileId().equals(r.get("fileId"))))allowed=true;if(!allowed)throw new SecurityException("Allegato non condiviso.");
        return files.read(request.fileId(),record.get("tenantId").toString(),record.get("createdBy").toString(),true);
    }
    public List<Map<String,Object>> shareAccesses(String antennaId,String shareId)throws Exception{writeAccess(true);asset(antennaId);var r=db().collection("antennaDossierShares").document(id(shareId));var s=r.get().get();owned(s,tenant());if(!s.exists()||!antennaId.equals(s.getString("antennaId")))throw new SecurityException("Link non autorizzato.");var rows=new ArrayList<Map<String,Object>>();for(var d:r.collection("accesses").orderBy("at",Query.Direction.DESCENDING).limit(100).get().get().getDocuments())rows.add(d.getData());return rows;}
    public Map<String,Object> file(String antennaId,String fileId)throws Exception{var d=get(antennaId);boolean found=false;for(String k:List.of("documents","photos"))if(rows(d,k).stream().anyMatch(r->fileId.equals(r.get("fileId"))))found=true;if(!found)throw new SecurityException("Allegato non presente nel fascicolo.");return files.read(fileId,tenant(),uid(),true);}
    public Map<String,Object> createSession(String antennaId,SessionCreate request)throws Exception{
        writeAccess(true);var a=asset(antennaId);var b=asset(request.peerAntennaId());if(a.getId().equals(b.getId()))throw bad("Seleziona due impianti distinti.");var oa=operators.getById(id(request.operatorAId()));var ob=operators.getById(id(request.operatorBId()));String t=tenant();
        if(oa==null||ob==null||!t.equals(oa.getTenantId())||!t.equals(ob.getTenantId())||!"ATTIVO".equalsIgnoreCase(oa.getStatus())||!"ATTIVO".equalsIgnoreCase(ob.getStatus())||oa.getFirebaseUid()==null||ob.getFirebaseUid()==null||oa.getFirebaseUid().equals(ob.getFirebaseUid()))throw bad("Servono due operatori attivi distinti con account associato.");
        String key=digest(t+":"+id(request.operationId()));var ref=db().collection("radioPairSessions").document(key);String hash=digest(json.writeValueAsString(request));var record=new LinkedHashMap<String,Object>();record.put("id",key);record.put("tenantId",t);record.put("title",required(Map.of("title",Objects.toString(request.title(),"")),"title",160));record.put("antennaIds",List.of(a.getId(),b.getId()));record.put("nameA",Objects.toString(a.getName(),a.getId()));record.put("nameB",Objects.toString(b.getName(),b.getId()));record.put("uidA",oa.getFirebaseUid());record.put("uidB",ob.getFirebaseUid());record.put("operatorA",Objects.toString(oa.getFullName(),oa.getId()));record.put("operatorB",Objects.toString(ob.getFullName(),ob.getId()));record.put("version",0L);record.put("state","OPEN");record.put("trials",List.of());record.put("createdAt",Instant.now().toString());record.put("createdBy",uid());record.put("hash",hash);
        return await(db().runTransaction(tx->{var prior=tx.get(ref).get();if(prior.exists()){owned(prior,t);if(!hash.equals(prior.getString("hash")))throw bad("Operazione già usata.");return sessionView(prior.getData());}tx.create(ref,record);return sessionView(record);}));
    }
    static Map<String,Object> sessionView(Map<String,Object> data){var out=new LinkedHashMap<>(data);out.remove("hash");out.remove("tenantId");return out;}
    static void sessionAccess(Map<String,Object> data,String tenant,String actor,boolean manage){if(!tenant.equals(data.get("tenantId"))||!manage&&!actor.equals(data.get("uidA"))&&!actor.equals(data.get("uidB")))throw new SecurityException("Sessione riservata ai partecipanti.");}
    public Map<String,Object> session(String key)throws Exception{readAccess();var d=db().collection("radioPairSessions").document(id(key)).get().get();if(!d.exists())throw bad("Sessione inesistente.");sessionAccess(d.getData(),tenant(),uid(),manager());return sessionView(d.getData());}
    public List<Map<String,Object>> sessions(String antennaId)throws Exception{asset(antennaId);String t=tenant(),actor=uid();boolean manage=manager();var out=new ArrayList<Map<String,Object>>();for(var d:db().collection("radioPairSessions").whereArrayContains("antennaIds",antennaId).limit(101).get().get().getDocuments()){var row=d.getData();if(t.equals(row.get("tenantId"))&&(manage||actor.equals(row.get("uidA"))||actor.equals(row.get("uidB"))))out.add(sessionView(row));}out.sort(Comparator.comparing((Map<String,Object> m)->m.get("createdAt").toString()).reversed());return out;}
    public Map<String,Object> trial(String key,Trial request)throws Exception{
        writeAccess(false);String t=tenant(),actor=uid(),op=id(request.operationId());boolean manage=manager();var ref=db().collection("radioPairSessions").document(id(key));var receipt=ref.collection("operations").document(op);String hash=digest(json.writeValueAsString(request));
        return await(db().runTransaction(tx->{var d=tx.get(ref).get();if(!d.exists())throw bad("Sessione inesistente.");sessionAccess(d.getData(),t,actor,manage);var prior=tx.get(receipt).get();if(prior.exists()){if(!hash.equals(prior.getString("hash")))throw bad("Operazione già usata.");return sessionView(d.getData());}var state=new LinkedHashMap<>(d.getData());if(version(state.get("version"))!=request.expectedVersion())throw conflict();if(!"OPEN".equals(state.get("state")))throw bad("Sessione già conclusa.");var trials=rows(state,"trials");String now=Instant.now().toString();
            switch(text(request.action(),20)){
                case "CREATE_TRIAL" -> {if(trials.size()>=100)throw bad("Massimo 100 prove per sessione.");var settings=strings(request.settings(),40);var trial=new LinkedHashMap<String,Object>();trial.put("id",op);trial.put("label",required(Map.of("label",Objects.toString(request.label(),"")),"label",160));trial.put("settings",settings);trial.put("createdBy",actor);trial.put("createdAt",now);trial.put("results",new LinkedHashMap<>());trials.add(trial);}
                case "RESULT" -> {String side=text(request.side(),1);if(!Set.of("A","B").contains(side)||!actor.equals(state.get("uid"+side)))throw new SecurityException("Puoi registrare solo la misura del tuo estremo.");var row=trials.stream().filter(r->id(request.trialId()).equals(r.get("id"))).findFirst().orElseThrow(()->bad("Prova inesistente."));var results=map(row.get("results"));if(results.containsKey(side))throw bad("Risultato già registrato: crea una nuova prova per correggerlo.");var result=new LinkedHashMap<String,Object>();result.put("metric",required(Map.of("metric",Objects.toString(request.metric(),"")),"metric",80));result.put("unit",text(request.unit(),20));result.put("value",number(request.value()));result.put("notes",text(request.notes(),2000));result.put("actor",actor);result.put("recordedAt",now);results.put(side,result);row.put("results",results);}
                case "CLOSE" -> {String note=text(request.notes(),2000);if(note.isBlank())throw bad("Esito finale obbligatorio.");if(trials.isEmpty()||trials.stream().anyMatch(r->map(r.get("results")).size()!=2))throw bad("Completa entrambe le misure di ogni prova prima di concludere.");state.put("state","CLOSED");state.put("conclusion",note);state.put("closedBy",actor);state.put("closedAt",now);}
                default -> throw bad("Azione non valida.");
            }
            state.put("trials",trials);state.put("version",request.expectedVersion()+1);state.put("updatedAt",now);bounded(state);tx.set(ref,state);tx.create(receipt,Map.of("hash",hash,"version",request.expectedVersion()+1));return sessionView(state);
        }));
    }
}
