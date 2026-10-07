package com.radiotech.radiotech_backend.ops;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.*;
import com.google.firebase.auth.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
/** Local acceptance checks with temporary fixtures; credentials/tokens are never printed. */
public final class AuthenticatedSmokeTool {
 public static void main(String[] args)throws Exception{
  if(args.length!=1||!URI.create(args[0]).getHost().equals("127.0.0.1"))throw new IllegalArgumentException("Local backend origin required");
  String project=System.getenv("FIREBASE_PROJECT_ID"),key=System.getenv("RADIOTECH_FIREBASE_WEB_API_KEY"),path=System.getenv("FIREBASE_SERVICE_ACCOUNT_PATH");if(project==null||key==null||path==null)throw new IllegalArgumentException("Explicit Firebase project, API key and service-account path required");
  try(var stream=java.nio.file.Files.newInputStream(java.nio.file.Path.of(path))){FirebaseApp.initializeApp(FirebaseOptions.builder().setProjectId(project).setCredentials(GoogleCredentials.fromStream(stream)).build());}
  try{
   var mapper=JsonMapper.builder().build();var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();UserRecord manager=null;
   for(var user:FirebaseAuth.getInstance().listUsers(null).iterateAll())if(!user.isDisabled()&&Set.of(com.radiotech.radiotech_backend.security.Role.ADMIN,com.radiotech.radiotech_backend.security.Role.SUPER_ADMIN,com.radiotech.radiotech_backend.security.Role.CHIEF_EXECUTIVE,com.radiotech.radiotech_backend.security.Role.NETWORK_MANAGER).contains(com.radiotech.radiotech_backend.security.Role.fromClaims(user.getCustomClaims()))&&user.getCustomClaims().get("tenantId")!=null&&!Boolean.TRUE.equals(user.getCustomClaims().get("mfaRequired"))){manager=user;break;}
   if(manager==null)throw new IllegalArgumentException("No eligible test manager exists");
   String custom=FirebaseAuth.getInstance().createCustomToken(manager.getUid());var authRequest=HttpRequest.newBuilder(URI.create("https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key="+key)).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("token",custom,"returnSecureToken",true)))).build();
   var authResponse=client.send(authRequest,HttpResponse.BodyHandlers.ofString());if(authResponse.statusCode()!=200)throw new IllegalStateException("Firebase smoke login failed: HTTP "+authResponse.statusCode());String token=mapper.readValue(authResponse.body(),Map.class).get("idToken").toString();
   if("true".equals(System.getenv("RADIOTECH_WEATHER_SMOKE_ONLY"))){
    var locations=(Map<?,?>)get(client,mapper,args[0]+"/api/v1/weather/locations?query=Lecce",token);
    if(!(locations.get("results") instanceof List<?> results)||results.isEmpty())throw new IllegalStateException("Weather geocoding returned no locations");
    var forecast=(Map<?,?>)get(client,mapper,args[0]+"/api/v1/weather?latitude=40.35&longitude=18.17",token);
    if(!(forecast.get("daily") instanceof Map<?,?> daily)||!(daily.get("time") instanceof List<?> days)||days.size()<6)throw new IllegalStateException("Five upcoming weather days unavailable");
    if(forecast.get("temperature_2m")==null||forecast.get("relative_humidity_2m")==null)throw new IllegalStateException("Current weather metrics unavailable");
    System.out.println("Authenticated weather: city search, current conditions, humidity and five upcoming days verified against live provider");return;
   }
   String qrOrigin=System.getenv("RADIOTECH_QR_SMOKE_ORIGIN");
   if(qrOrigin==null||qrOrigin.isBlank())qrOrigin=args[0];else{var uri=URI.create(qrOrigin);if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!uri.getPath().isEmpty())throw new IllegalArgumentException("QR smoke requires an explicit HTTPS origin");}
   qrFirstLogin(client,mapper,qrOrigin,key,manager.getCustomClaims().get("tenantId").toString(),token);
   var catalog=(List<Map<String,Object>>)get(client,mapper,args[0]+"/api/v1/pro/catalog",token);for(var module:catalog)get(client,mapper,args[0]+"/api/v1/pro/"+module.get("id"),token);System.out.println("Authenticated Pro catalog and "+catalog.size()+" sections: HTTP 200");
   var antennas=(List<Map<String,Object>>)get(client,mapper,args[0]+"/api/v1/antennas",token);if(!antennas.isEmpty()){String id=antennas.getFirst().get("id").toString();var qr=(Map<String,Object>)get(client,mapper,args[0]+"/api/v1/antennas/"+id+"/qr",token);byte[] png=Base64.getDecoder().decode(qr.get("imageDataUrl").toString().split(",")[1]);var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));var binary=new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(new com.google.zxing.client.j2se.BufferedImageLuminanceSource(image)));String decoded=new com.google.zxing.MultiFormatReader().decode(binary).getText();if(!id.equals(decoded))throw new IllegalStateException("QR payload mismatch");var resolved=(Map<String,Object>)get(client,mapper,args[0]+"/api/v1/antennas/resolve/"+id,token);if(!id.equals(resolved.get("id")))throw new IllegalStateException("Mobile QR resolve mismatch");System.out.println("Antenna QR: authenticated HTTP 200, PNG decoded, mobile resolution matched");}else System.out.println("Antenna QR live check skipped: no assets in the test manager tenant");
   var storage=(Map<String,Object>)get(client,mapper,args[0]+"/api/v1/files/config",token);
   if("LOCAL".equals(storage.get("mode"))){byte[] content="RadioTech free attachment smoke test".getBytes();var uploadRequest=HttpRequest.newBuilder(URI.create(args[0]+"/api/v1/files?name=smoke-test.txt&operationId=smoke_"+UUID.randomUUID())).timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+token).header("Content-Type","application/octet-stream").POST(HttpRequest.BodyPublishers.ofByteArray(content)).build();var upload=client.send(uploadRequest,HttpResponse.BodyHandlers.ofString());if(upload.statusCode()!=200)throw new IllegalStateException("Local attachment upload failed: HTTP "+upload.statusCode());var saved=mapper.readValue(upload.body(),Map.class);String id=saved.get("reference").toString().substring("radiotech-file:".length());var downloaded=(Map<String,Object>)get(client,mapper,args[0]+"/api/v1/files/"+id,token);if(!Arrays.equals(content,Base64.getDecoder().decode(downloaded.get("base64").toString())))throw new IllegalStateException("Local attachment content mismatch");System.out.println("Free local attachments: authenticated upload/download and integrity verified");}
   var ai=(Map<String,Object>)get(client,mapper,args[0]+"/api/v1/ai/status",token);System.out.println("Local AI backend enabled: "+ai.get("enabled"));get(client,mapper,args[0]+"/actuator/health/readiness",token);System.out.println("Authenticated backend readiness: HTTP 200");
  }finally{FirebaseApp.getInstance().delete();}
 }
 private static void qrFirstLogin(HttpClient client,JsonMapper mapper,String origin,String key,String tenant,String managerToken)throws Exception{
  String id="qr-smoke-"+UUID.randomUUID(),email=id+"@example.test",badge="AUTH_OP_"+UUID.randomUUID();
  var db=com.google.firebase.cloud.FirestoreClient.getFirestore();var ref=db.collection("operators").document(id);String uid=null,deviceId=null;
  try{
   ref.set(Map.of("tenantId",tenant,"fullName","Temporary QR acceptance test","email",email,"status","ATTIVO","role","OPERATOR","qrCodeToken",badge,"qrExpiresAt",java.time.Instant.now().plusSeconds(300).toString())).get();
   var response=post(client,mapper,origin+"/api/v1/auth/qr-login",null,Map.of("qrToken",badge));
   var operator=(Map<?,?>)response.get("operator");uid=operator.get("firebaseUid").toString();
   var firebase=post(client,mapper,"https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key="+key,null,Map.of("token",response.get("customToken"),"returnSecureToken",true));String idToken=firebase.get("idToken").toString();
   get(client,mapper,origin+"/api/v1/auth/me",idToken);
   var device=post(client,mapper,origin+"/api/v1/session/devices",idToken,Map.of("installationId",UUID.randomUUID().toString(),"platform","QR acceptance test"));deviceId=device.get("deviceId").toString();
   var bound=post(client,mapper,"https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key="+key,null,Map.of("token",device.get("customToken"),"returnSecureToken",true));
   get(client,mapper,origin+"/api/v1/auth/me",bound.get("idToken").toString());
   String operatorToken=bound.get("idToken").toString();
   var personal=(Map<?,?>)get(client,mapper,origin+"/api/v1/operator/me/badge",operatorToken);
   var webBadge=(Map<?,?>)get(client,mapper,origin+"/api/v1/operators/"+id+"/badge",managerToken);
   if(!mapper.readValue(personal.get("payload").toString(),Map.class).get("token").equals(webBadge.get("qrCodeToken"))||!personal.get("imageDataUrl").equals(webBadge.get("imageDataUrl")))throw new IllegalStateException("Web and mobile personal badge differ");
   get(client,mapper,origin+"/api/v1/calendar",operatorToken);
   get(client,mapper,origin+"/api/v1/operator/me/reports",operatorToken);
   System.out.println("Personal badge: identical web/mobile payload and PNG; calendar and intervention history authorized over HTTPS");
   reportAcceptance(client,mapper,origin,tenant,uid,id,operatorToken,managerToken);
   var repeated=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/v1/auth/qr-login")).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("qrToken",badge)))).build(),HttpResponse.BodyHandlers.ofString());
   if(repeated.statusCode()!=401)throw new IllegalStateException("Consumed QR must be rejected");
   System.out.println("First QR login: unlinked operator provisioned, Firebase exchanged, device bound, profile readable; badge reuse rejected");
  }finally{
   if(deviceId!=null)db.collection("pro_devices").document(deviceId).delete().get();
   if(uid==null){try{uid=FirebaseAuth.getInstance().getUserByEmail(email).getUid();}catch(FirebaseAuthException notFound){if(notFound.getAuthErrorCode()!=AuthErrorCode.USER_NOT_FOUND)throw notFound;}}
   if(uid!=null)FirebaseAuth.getInstance().deleteUser(uid);ref.delete().get();
  }
 }
 private static void reportAcceptance(HttpClient client,JsonMapper mapper,String origin,String tenant,String uid,String operatorId,String operatorToken,String managerToken)throws Exception{
  var db=com.google.firebase.cloud.FirestoreClient.getFirestore();String id="report-smoke-"+UUID.randomUUID(),requestKey=UUID.randomUUID().toString(),reportId=null,attachmentId=null;
  var antenna=db.collection("antennas").document(id);var task=db.collection("tasks").document(id);
  try{
   antenna.set(Map.of("tenantId",tenant,"name","Temporary report acceptance asset","status","ATTIVA","lat",41.1,"lng",16.8)).get();
   var profile=(Map<?,?>)get(client,mapper,origin+"/api/v1/operator/me",operatorToken);
   var namedAsset=(Map<?,?>)get(client,mapper,origin+"/api/v1/antennas/"+id,operatorToken);
   if(!"Temporary QR acceptance test".equals(profile.get("fullName"))||!"Temporary report acceptance asset".equals(namedAsset.get("name")))throw new IllegalStateException("Operator report metadata is not readable");
   System.out.println("Report document metadata: original antenna name and authenticated operator name readable over HTTPS");
   task.set(Map.of("tenantId",tenant,"title","Temporary report acceptance task","status","ASSIGNED","operatorId",operatorId,"operatorFirebaseUid",uid,"antennaId",id)).get();
   var assigned=(List<Map<String,Object>>)get(client,mapper,origin+"/api/v1/operator/me/tasks",operatorToken);
   if(assigned.stream().noneMatch(t->id.equals(t.get("id"))))throw new IllegalStateException("Assigned task missing from mobile list");
   for(String action:List.of("accept","en-route","check-in","start")){
    var step=post(client,mapper,origin+"/api/v1/operator/tasks/"+id+"/"+action,operatorToken,
      action.equals("check-in")?Map.of("latitude",41.1,"longitude",16.8,"accuracy",8.0):Map.of());
    String expected=switch(action){case "accept"->"ACCEPTED";case "en-route"->"EN_ROUTE";case "check-in"->"CHECKED_IN";default->"IN_PROGRESS";};
    if(!expected.equals(step.get("status")))throw new IllegalStateException("Unexpected mobile workflow status after "+action);
   }
   System.out.println("Mobile assignments: personal list includes task; accept, en-route, GPS check-in and start verified over HTTPS");
   var config=(Map<?,?>)get(client,mapper,origin+"/api/v1/files/config",operatorToken);var attachments=new ArrayList<String>();
   if("LOCAL".equals(config.get("mode"))){
    byte[] bytes="%PDF-1.4\nRadioTech temporary attachment acceptance test\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    String sample=System.getenv("RADIOTECH_REPORT_SMOKE_PDF");
    if(sample!=null&&!sample.isBlank()){bytes=java.nio.file.Files.readAllBytes(java.nio.file.Path.of(sample));if(bytes.length<5||bytes.length>10000000||bytes[0]!=37||bytes[1]!=80||bytes[2]!=68||bytes[3]!=70)throw new IllegalArgumentException("Valid PDF acceptance fixture required");}
    var uploaded=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/v1/files?name=report.pdf&operationId="+requestKey+"_pdf")).timeout(Duration.ofSeconds(25)).header("Authorization","Bearer "+operatorToken).header("Content-Type","application/octet-stream").POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),HttpResponse.BodyHandlers.ofString());
    if(uploaded.statusCode()!=200)throw new IllegalStateException("Operator PDF upload denied: HTTP "+uploaded.statusCode());String reference=mapper.readValue(uploaded.body(),Map.class).get("reference").toString();attachments.add(reference);attachmentId=reference.substring("radiotech-file:".length());
    var downloaded=(Map<?,?>)get(client,mapper,origin+"/api/v1/files/"+attachmentId,operatorToken);if(!Arrays.equals(bytes,Base64.getDecoder().decode(downloaded.get("base64").toString())))throw new IllegalStateException("Operator PDF download mismatch");
   }
   var payload=Map.<String,Object>of("antennaId",id,"operatorNotes","Temporary report acceptance test","description","Temporary report acceptance test","workPerformed","QA","latitude",41.1,"longitude",16.8,"completedAt",java.time.Instant.now().toString(),"attachments",attachments,"checklist",Map.of("QA",true));
   var request=HttpRequest.newBuilder(URI.create(origin+"/api/v1/operator/tasks/"+id+"/report")).timeout(Duration.ofSeconds(25)).header("Authorization","Bearer "+operatorToken).header("Content-Type","application/json").header("Idempotency-Key",requestKey).POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
   var submitted=client.send(request,HttpResponse.BodyHandlers.ofString());if(submitted.statusCode()!=201)throw new IllegalStateException("Report submission failed: HTTP "+submitted.statusCode());
   var report=(Map<?,?>)mapper.readValue(submitted.body(),Map.class).get("report");reportId=report.get("id").toString();
   var replay=client.send(request,HttpResponse.BodyHandlers.ofString());if(replay.statusCode()!=201||!reportId.equals(((Map<?,?>)mapper.readValue(replay.body(),Map.class).get("report")).get("id")))throw new IllegalStateException("Report retry must return the same report");
   var afterSubmission=(List<Map<String,Object>>)get(client,mapper,origin+"/api/v1/operator/me/tasks",operatorToken);
   if(afterSubmission.stream().noneMatch(t->id.equals(t.get("id"))&&"REPORT_SUBMITTED".equals(t.get("status"))))throw new IllegalStateException("Submitted task missing from mobile review list");
   var visible=(List<Map<String,Object>>)get(client,mapper,origin+"/api/v1/reports",managerToken);String expected=reportId;if(visible.stream().noneMatch(r->expected.equals(r.get("id"))))throw new IllegalStateException("Submitted mobile report is missing from the web report list");
   System.out.println("Mobile operator attachments: config/upload/owned download authorized; report closure HTTP 201, identical retry returned the same ID, web manager list contains the report");
  }finally{
   for(var record:db.collection("maintenanceReports").whereEqualTo("taskId",id).get().get().getDocuments())record.getReference().delete().get();
   byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest((tenant+"|"+uid+"|"+requestKey).getBytes(java.nio.charset.StandardCharsets.UTF_8));db.collection("apiIdempotencyKeys").document(HexFormat.of().formatHex(digest)).delete().get();
   task.delete().get();antenna.delete().get();
   if(attachmentId!=null&&attachmentId.matches("[a-f0-9]{64}")){java.nio.file.Path files=java.nio.file.Path.of(".dist/private-files");java.nio.file.Files.deleteIfExists(files.resolve(attachmentId+".json"));java.nio.file.Files.deleteIfExists(files.resolve(attachmentId+".bin"));}
  }
 }
 private static Map<String,Object> post(HttpClient client,JsonMapper mapper,String url,String token,Map<String,Object> body)throws Exception{
  var builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25)).header("Content-Type","application/json");if(token!=null)builder.header("Authorization","Bearer "+token);
  var response=client.send(builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
  if(response.statusCode()!=200)throw new IllegalStateException("QR acceptance step failed: "+URI.create(url).getPath()+" HTTP "+response.statusCode());return mapper.readValue(response.body(),Map.class);
 }
 private static Object get(HttpClient client,JsonMapper mapper,String url,String token)throws Exception{var response=client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25)).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IllegalStateException("Smoke endpoint failed: "+URI.create(url).getPath()+" HTTP "+response.statusCode()+" "+mapper.readValue(response.body(),Map.class).get("message"));Object body=mapper.readValue(response.body(),Object.class);return body instanceof Map<?,?> m&&m.get("data")!=null?m.get("data"):body;}
}
