package com.radiotech.radiotech_backend.workforce;

import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.model.Operator;
import com.radiotech.radiotech_backend.security.*;
import com.radiotech.radiotech_backend.service.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class RosterService {
    private final OperatorService operators; private final NotificationService notifications; private final RosterSolver solver;
    private final JsonMapper json=JsonMapper.builder().build();
    public RosterService(OperatorService operators,NotificationService notifications,RosterSolver solver) { this.operators=operators;this.notifications=notifications;this.solver=solver; }
    static String tenant() { return OperationalInsightsService.requireIdentity(); }
    static String uid() { String uid=SecurityContextAccessor.currentUid();if(uid==null)throw new SecurityException("Autenticazione richiesta.");return uid; }
    static String key(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    static YearMonth month(String value) { YearMonth month=YearMonth.parse(value); if(month.getYear()<2020||month.getYear()>2100)throw new IllegalArgumentException("Mese non valido.");return month; }
    static DocumentReference plan(String tenant,String month) { return FirestoreClient.getFirestore().collection("workforcePlans").document(key(tenant+":"+month)); }
    static DocumentReference guard(String tenant) { return FirestoreClient.getFirestore().collection("workforcePlanGuards").document(key(tenant)); }
    static long number(Object o) { return o instanceof Number n?n.longValue():0; }
    static ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT,"Calendario modificato. Aggiorna prima di proseguire."); }
    static <T> T transact(com.google.api.core.ApiFuture<T> future) throws Exception {
        try { return future.get(); } catch(ExecutionException e) { if(e.getCause() instanceof RuntimeException r)throw r;throw e; }
    }
    public List<Map<String,Object>> members() throws Exception {
        WorkforceService.requireManager();
        return operators.getAllOperators().stream().filter(o->"ATTIVO".equalsIgnoreCase(o.getStatus())&&o.getFirebaseUid()!=null&&!o.getFirebaseUid().isBlank())
                .map(o->Map.<String,Object>of("operatorId",o.getId(),"name",Objects.toString(o.getFullName(),o.getId()),"uid",o.getFirebaseUid(),"specialization",Objects.toString(o.getSpecialization(),""))).toList();
    }
    public Map<String,Object> draft(String value) throws Exception {
        WorkforceService.requireManager(); month(value); var doc=plan(tenant(),value).get().get();
        return doc.exists()?new LinkedHashMap<>(doc.getData()):Map.of("month",value,"version",0L,"status","EMPTY");
    }
    private Roster.Config config(Map<String,Object> doc) { var c=json.convertValue(doc.get("config"),Roster.Config.class); return new Roster.Config(c.rules(),c.templates(),c.members(),c.assignments(),number(doc.get("version")),c.notes()); }
    @SuppressWarnings("unchecked") private Map<String,Object> map(Object record) { return json.convertValue(record,Map.class); }
    private Map<String,Operator> resolve(Roster.Config config) throws Exception {
        var result=new HashMap<String,Operator>();
        for(var m:config.members()) {
            var op=operators.getById(m.operatorId());
            if(op==null || !tenant().equals(op.getTenantId()) || !"ATTIVO".equalsIgnoreCase(op.getStatus()) || op.getFirebaseUid()==null || op.getFirebaseUid().isBlank())
                throw new IllegalArgumentException("Operatore non attivo o non assegnato: "+m.operatorId());
            result.put(op.getId(),op);
        }
        if(result.values().stream().map(Operator::getFirebaseUid).distinct().count()!=result.size())throw new IllegalArgumentException("Due operatori associati allo stesso account.");
        return result;
    }
    private List<Roster.Slot> slots(Roster.Config c,Map<String,Operator> members) {
        var templates=new HashMap<String,Roster.Template>(); c.templates().forEach(t->{if(templates.put(t.id(),t)!=null)throw new IllegalArgumentException("Modello duplicato.");});
        return c.assignments().stream().map(a->{var t=templates.get(a.templateId());var op=members.get(a.operatorId());if(t==null||op==null)throw new IllegalArgumentException("Riferimento turno non valido.");return Roster.slot(a,t,c.rules(),op.getFirebaseUid(),Objects.toString(op.getFullName(),op.getId()));}).toList();
    }
    private Map<String,Object> mutate(String value,Roster.Config c,String status,long guardVersion,Roster.Result result,boolean publish) throws Exception {
        String tenant=tenant(),actor=uid(); var db=FirestoreClient.getFirestore(); var ref=plan(tenant,value); var global=guard(tenant);
        var people=resolve(c); var resolved=slots(c,people); var now=Instant.now().toString();
        Map<String,Object> config=map(c);
        if(json.writeValueAsBytes(config).length>600000)throw new IllegalArgumentException("Calendario troppo grande.");
        return transact(db.runTransaction(tx->{
            var old=tx.get(ref).get();var g=tx.get(global).get();long version=old.exists()?number(old.get("version")):0;
            if(version!=c.expectedVersion() || (guardVersion>=0 && number(g.get("version"))!=guardVersion))throw conflict();
            var document=old.exists()?new LinkedHashMap<>(old.getData()):new LinkedHashMap<String,Object>();
            document.put("tenantId",tenant);document.put("month",value);document.put("version",version+1);document.put("status",status);
            document.put("config",config);document.put("updatedAt",now);document.put("updatedBy",actor);
            if(result!=null)document.put("generation",map(result));
            if(publish) {
                long revision=number(document.get("publishedRevision"))+1;
                var snapshot=new LinkedHashMap<String,Object>();snapshot.put("tenantId",tenant);snapshot.put("month",value);snapshot.put("revision",revision);
                snapshot.put("publishedAt",now);snapshot.put("publishedBy",actor);snapshot.put("zone",c.rules().zone());snapshot.put("toleranceMinutes",c.rules().toleranceMinutes());
                snapshot.put("notes",Objects.toString(c.notes(),""));snapshot.put("slots",resolved.stream().map(this::map).toList());
                String publishedId=key(tenant+":"+value+":"+revision); snapshot.put("id",publishedId);
                tx.create(db.collection("workforcePlanVersions").document(publishedId),snapshot);
                document.put("publishedRevision",revision);document.put("published",snapshot);
            }
            tx.set(ref,document);tx.set(global,Map.of("tenantId",tenant,"version",number(g.get("version"))+1));
            tx.create(db.collection("workforceRosterAudit").document(),Map.of("tenantId",tenant,"actor",actor,"action",publish?"PUBLISH":"SAVE","month",value,"version",version+1,"createdAt",now));
            return document;
        }));
    }
    public Map<String,Object> save(String value,Roster.Config c) throws Exception {
        WorkforceService.requireManager();var m=month(value);
        for(var a:c.assignments())if(!m.equals(YearMonth.from(LocalDate.parse(a.date()))))throw new IllegalArgumentException("Assegnazione fuori mese.");
        return mutate(value,c,"DRAFT",-1,null,false);
    }
    private List<Roster.Slot> boundary(String tenant,YearMonth month) throws Exception {
        var result=new ArrayList<Roster.Slot>();
        for(var adjacent:List.of(month.minusMonths(1),month.plusMonths(1))) {
            var snapshot=published(tenant,adjacent.toString());
            for(var row:rows(snapshot,"slots"))result.add(json.convertValue(row,Roster.Slot.class));

        }
        return result;
    }
    public Map<String,Object> generate(String value,Roster.Version request) throws Exception {
        WorkforceService.requireManager();String tenant=tenant();long guardVersion=number(guard(tenant).get().get().get("version"));
        var doc=draft(value);if(number(doc.get("version"))!=request.expectedVersion())throw conflict();
        if(!doc.containsKey("config"))throw new IllegalArgumentException("Salva prima i vincoli.");
        var c=config(doc);resolve(c);var result=solver.solve(month(value),c,boundary(tenant,month(value)),false);
        if(!Set.of("OPTIMAL","FEASIBLE").contains(result.status()))return Map.of("generated",false,"result",result);
        var generated=new Roster.Config(c.rules(),c.templates(),c.members(),result.assignments(),request.expectedVersion(),c.notes());
        return Map.of("generated",true,"result",result,"plan",mutate(value,generated,"DRAFT",guardVersion,result,false));
    }
    public Map<String,Object> publish(String value,Roster.Version request) throws Exception {
        WorkforceService.requireManager();String tenant=tenant();long guardVersion=number(guard(tenant).get().get().get("version"));
        var doc=draft(value);if(number(doc.get("version"))!=request.expectedVersion())throw conflict();
        if(!doc.containsKey("config"))throw new IllegalArgumentException("Calendario non disponibile.");
        var c=config(doc);var result=solver.solve(month(value),c,boundary(tenant,month(value)),true);
        if(!Set.of("OPTIMAL","FEASIBLE").contains(result.status()))throw new IllegalArgumentException(String.join(" ",result.diagnostics()));
        var saved=mutate(value,c,"PUBLISHED",guardVersion,result,true);
        return Map.of("plan",saved,"notice","Calendario pubblicato. Avvia l'invio delle notifiche per comunicare la nuova versione.");
    }
    public Map<String,Object> notifyPublished(String value) throws Exception {
        WorkforceService.requireManager();var p=published(tenant(),month(value).toString());if(p.isEmpty())throw new IllegalArgumentException("Nessuna versione pubblicata.");
        int delivered=0;var failed=new ArrayList<String>();
        for(String id:rows(p,"slots").stream().map(s->s.get("operatorId").toString()).distinct().toList()) {
            try { delivered+=notifications.notifyOperator(id,"Turni pubblicati: "+value,"È disponibile il calendario mensile, versione "+p.get("revision")+". Apri Presenze e turni nell'app.",Map.of("type","ROSTER_PUBLISHED","month",value,"revision",p.get("revision").toString())); }
            catch(Exception e) { failed.add(id); }
        }
        return Map.of("deliveredDevices",delivered,"failedOperators",failed,"revision",p.get("revision"));
    }
    @SuppressWarnings("unchecked") static List<Map<String,Object>> rows(Map<String,Object> doc,String field) { return doc.get(field) instanceof List<?> l ? (List<Map<String,Object>>)l : List.of(); }
    static Map<String,Object> published(String tenant,String month) throws Exception {
        var doc=plan(tenant,month).get().get();return doc.exists()&&doc.get("published") instanceof Map<?,?> p?new LinkedHashMap<>((Map<String,Object>)p):Map.of();
    }
    public Map<String,Object> personal(String value) throws Exception {
        String uid=uid();var p=published(tenant(),month(value).toString());
        if(p.isEmpty())return Map.of("month",value,"status","UNPUBLISHED","slots",List.of());
        p.remove("tenantId");p.remove("publishedBy");p.remove("notes"); // Team notes are not a personal document.
        p.put("slots",rows(p,"slots").stream().filter(s->uid.equals(s.get("uid"))).toList());p.put("status","PUBLISHED");return p;
    }
    public Map<String,Object> forPdf(String value,String operatorId) throws Exception {
        Map<String,Object> p;
        if(operatorId==null) { p=new LinkedHashMap<>(personal(value));var own=operators.getByFirebaseUid(uid());p.put("operatorName",own!=null&&tenant().equals(own.getTenantId())?Objects.toString(own.getFullName(),uid()):uid()); }
        else { WorkforceService.requireManager();var op=operators.getById(operatorId);if(op==null||!tenant().equals(op.getTenantId()))throw new SecurityException("Operatore non autorizzato.");p=new LinkedHashMap<>(published(tenant(),month(value).toString()));p.put("slots",rows(p,"slots").stream().filter(s->operatorId.equals(s.get("operatorId"))).toList());p.put("operatorName",Objects.toString(op.getFullName(),operatorId)); }
        if(!p.containsKey("revision"))throw new IllegalArgumentException("Calendario non ancora pubblicato.");
        if(!rows(p,"slots").isEmpty())p.put("operatorName",rows(p,"slots").getFirst().get("name"));return p;
    }
}
