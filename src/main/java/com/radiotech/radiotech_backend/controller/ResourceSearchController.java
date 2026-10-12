package com.radiotech.radiotech_backend.controller;

import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@RestController
@RequestMapping("/api/v1/dashboard/search")
public class ResourceSearchController {
 public Map<String,Object> search(String q)throws Exception{return search(q,null);}
 @GetMapping public Map<String,Object> search(@RequestParam String q,@RequestParam(required=false) String cursor)throws Exception {
  var role=SecurityContextAccessor.currentRole();
  if(role==Role.OPERATOR||role==Role.CUSTOMER||!TenantAccessPolicy.canAccess(role,Permission.ANALYTICS_READ))throw new SecurityException("Permesso insufficiente.");
  String tenant=TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(),null);
  String term=java.text.Normalizer.normalize(q.trim(),java.text.Normalizer.Form.NFKD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
  if(term.length()<2||term.length()>32)throw new IllegalArgumentException("Inserisci da 2 a 32 caratteri.");
  var db=FirestoreClient.getFirestore();var summary=db.collection("dashboardSummaries").document(tenant).get().get();
  boolean ready=summary.exists()&&Boolean.TRUE.equals(summary.getBoolean("ready"))&&tenant.equals(summary.getString("tenantId"));
  var permissions=Map.of("antennas",Permission.ASSET_READ,"operators",Permission.USER_READ,"tasks",Permission.TASK_READ,"inventory",Permission.INVENTORY_READ,"maintenanceReports",Permission.REPORT_READ);
  if(!ready||cursor!=null&&cursor.startsWith("legacy:"))return legacySearch(tenant,role,term,cursor,permissions);
  var rows=new ArrayList<Map<String,Object>>();
  var query=db.collection("resourceSearch").whereEqualTo("tenantId",tenant).whereArrayContains("prefixes",term).orderBy(com.google.cloud.firestore.FieldPath.documentId());
  if(cursor!=null){if(cursor.contains("/")||cursor.length()>1500)throw new IllegalArgumentException("Cursore non valido.");query=query.startAfter(cursor);}
  var hits=query.limit(20).get().get().getDocuments();
  for(var hit:hits) {
   String collection=hit.getString("collection"),id=hit.getString("resourceId");
   if(!permissions.containsKey(collection)||!TenantAccessPolicy.canAccess(role,permissions.get(collection))||id==null||id.contains("/"))continue;
   var current=db.collection(collection).document(id).get().get();
   if(!current.exists()||!tenant.equals(current.getString("tenantId"))||current.get("removedAt")!=null&&!current.get("removedAt").toString().isBlank())continue;
   var fields=current.getData();Object label=fields.get("name");for(String key:List.of("fullName","full_name","title","taskTitle"))if(label==null)label=fields.get(key);
   rows.add(Map.of("id",id,"type",collection,"label",Objects.toString(label,id),"status",Objects.toString(fields.get("status"),""),"updatedAt",Objects.toString(fields.get("updatedAt"),Objects.toString(hit.get("updatedAt"),""))));
  }
  var result=new LinkedHashMap<String,Object>();result.put("items",rows);result.put("nextCursor",hits.size()==20?hits.getLast().getId():null);result.put("limit",20);return result;
 }
 private record LegacyCursor(String tenant,String term,Map<String,String> after,Set<String> done){}
 private Map<String,Object> legacySearch(String tenant,Role role,String term,String cursor,Map<String,Permission> permissions)throws Exception{
  var after=new LinkedHashMap<String,String>();var done=new HashSet<String>();
  if(cursor!=null){if(!cursor.startsWith("legacy:")||cursor.length()>10000)throw new IllegalArgumentException("Cursore non valido.");
   try{var previous=new com.google.gson.Gson().fromJson(new String(Base64.getUrlDecoder().decode(cursor.substring(7)),java.nio.charset.StandardCharsets.UTF_8),LegacyCursor.class);if(!tenant.equals(previous.tenant())||!term.equals(previous.term()))throw new IllegalArgumentException();after.putAll(previous.after());done.addAll(previous.done());}catch(Exception failure){throw new IllegalArgumentException("Cursore non valido.");}
  }
  var rows=new ArrayList<Map<String,Object>>();var pages=new com.radiotech.radiotech_backend.service.DashboardService();
  for(String collection:List.of("antennas","operators","tasks","inventory","maintenanceReports")){
   if(done.contains(collection)||!TenantAccessPolicy.canAccess(role,permissions.get(collection)))continue;
   var page=pages.resourcePage(collection,after.get(collection),Math.max(1,20-rows.size()),term,"","","","");
   for(Object raw:(List<?>)page.get("items")){var item=(Map<?,?>)raw;if(item.get("removedAt")!=null&&!item.get("removedAt").toString().isBlank())continue;
    Object label=item.get("name");for(String key:List.of("fullName","full_name","title","taskTitle"))if(label==null)label=item.get(key);
    rows.add(Map.of("id",item.get("id"),"type",collection,"label",Objects.toString(label,item.get("id").toString()),"status",Objects.toString(item.get("status"),""),"updatedAt",Objects.toString(item.get("updatedAt"),"non disponibile")));
   }
   Object next=page.get("nextCursor");if(next==null)done.add(collection);else after.put(collection,next.toString());
   if(rows.size()>=20)break;
  }
  boolean more=permissions.entrySet().stream().anyMatch(entry->TenantAccessPolicy.canAccess(role,entry.getValue())&&!done.contains(entry.getKey()));
  var result=new LinkedHashMap<String,Object>();result.put("items",rows);result.put("nextCursor",more?"legacy:"+Base64.getUrlEncoder().withoutPadding().encodeToString(new com.google.gson.Gson().toJson(new LegacyCursor(tenant,term,after,done)).getBytes(java.nio.charset.StandardCharsets.UTF_8)):null);result.put("limit",20);return result;
 }
}
