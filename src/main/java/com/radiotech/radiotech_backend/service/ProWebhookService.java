package com.radiotech.radiotech_backend.service;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.json.JsonMapper;
@Service
public class ProWebhookService {
 @Value("${radiotech.pro.webhook-hosts:}") private String hosts="";
 @Value("${radiotech.pro.webhook-secret:}") private String secret="";
 private static final java.util.concurrent.ScheduledExecutorService TIMEOUTS=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"webhook-timeout");t.setDaemon(true);return t;});
 public void validateEndpoint(String endpoint)throws Exception{
  var uri=URI.create(endpoint);if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||(uri.getPort()!=-1&&uri.getPort()!=443))throw new IllegalArgumentException("Webhook HTTPS sulla porta 443 richiesto.");
  if(secret.length()<32||Arrays.stream(hosts.split(",")).map(String::trim).noneMatch(h->h.equalsIgnoreCase(uri.getHost())))throw new IllegalArgumentException("Prima configurare host autorizzato e secret del webhook nel deploy.");
  for(var address:InetAddress.getAllByName(uri.getHost()))if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress()||(address.getAddress().length==16&&(address.getAddress()[0]&0xfe)==0xfc))throw new SecurityException("Destinazione privata non consentita.");
 }
 public void deliver(String eventId)throws Exception{
  var db=FirestoreClient.getFirestore();var ref=db.collection("proWebhookOutbox").document(eventId);String lease=UUID.randomUUID().toString();
  var claimed=db.runTransaction(tx->{var doc=tx.get(ref).get();if(!doc.exists()||Set.of("DELIVERED","FAILED").contains(doc.getString("status")))return null;if(doc.getString("nextAttemptAt")!=null&&Instant.parse(doc.getString("nextAttemptAt")).isAfter(Instant.now()))return null;if("DELIVERING".equals(doc.getString("status"))&&doc.getString("leaseUntil")!=null&&Instant.parse(doc.getString("leaseUntil")).isAfter(Instant.now()))return null;tx.update(ref,Map.of("status","DELIVERING","lease",lease,"leaseUntil",Instant.now().plusSeconds(120).toString()));return doc.getData();}).get();
  if(claimed==null)return;int status=0;String failure="";
  try{
   var integration=db.collection("pro_integrations").document(claimed.get("integrationId").toString()).get().get();if(!integration.exists()||!claimed.get("tenantId").equals(integration.get("tenantId"))||!Boolean.TRUE.equals(integration.get("deliveryEnabled")))throw new IllegalArgumentException("Integrazione disattivata.");
   String endpoint=integration.getString("endpoint");validateEndpoint(endpoint);String body=JsonMapper.builder().build().writeValueAsString(claimed.get("payload"));String timestamp=Instant.now().toString();
   var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));String signature=HexFormat.of().formatHex(mac.doFinal((eventId+"."+timestamp+"."+body).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
   status=postPinned(URI.create(endpoint),eventId,timestamp,signature,body);if(status<200||status>=300)failure="HTTP_"+status;
  }catch(Exception e){failure=e.getClass().getSimpleName();}
  boolean success=status>=200&&status<300;int responseCode=status;String error=failure;
  db.runTransaction(tx->{var current=tx.get(ref).get();if(!lease.equals(current.getString("lease")))return false;long attempts=current.get("attempts") instanceof Number n?n.longValue()+1:1L;tx.update(ref,Map.of("status",success?"DELIVERED":attempts>=8?"FAILED":"PENDING","attempts",attempts,"lastAttemptAt",Instant.now().toString(),"responseCode",responseCode,"lastError",error,"nextAttemptAt",Instant.now().plusSeconds(Math.min(3600,30L*(1L<<Math.min(attempts,7)))).toString()));return true;}).get();
 }
 private int postPinned(URI uri,String eventId,String timestamp,String signature,String body)throws Exception{
  byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);if(bytes.length>131072)throw new IllegalArgumentException("Webhook payload too large");
  var addresses=InetAddress.getAllByName(uri.getHost());for(var address:addresses)if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress()||(address.getAddress().length==16&&(address.getAddress()[0]&0xfe)==0xfc))throw new SecurityException("Private destination rejected");
  try(var raw=new Socket()){
   var deadline=TIMEOUTS.schedule(()->{try{raw.close();}catch(Exception ignored){}},15,java.util.concurrent.TimeUnit.SECONDS);
   try{raw.connect(new InetSocketAddress(addresses[0],443),8000);raw.setSoTimeout(10000);
    try(var tls=(javax.net.ssl.SSLSocket)((javax.net.ssl.SSLSocketFactory)javax.net.ssl.SSLSocketFactory.getDefault()).createSocket(raw,uri.getHost(),443,true)){
     var parameters=tls.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");parameters.setServerNames(List.of(new javax.net.ssl.SNIHostName(uri.getHost())));tls.setSSLParameters(parameters);tls.startHandshake();
     String path=uri.getRawPath().isBlank()?"/":uri.getRawPath();if(uri.getRawQuery()!=null)path+="?"+uri.getRawQuery();
     String header="POST "+path+" HTTP/1.1\r\nHost: "+uri.getHost()+"\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\nX-RadioTech-Event: "+eventId+"\r\nX-RadioTech-Timestamp: "+timestamp+"\r\nX-RadioTech-Signature: sha256="+signature+"\r\n\r\n";
     var output=tls.getOutputStream();output.write(header.getBytes(java.nio.charset.StandardCharsets.US_ASCII));output.write(bytes);output.flush();
     var input=tls.getInputStream();var line=new StringBuilder();for(int i=0;i<8192;i++){int next=input.read();if(next==-1)throw new java.io.IOException("Incomplete HTTP response");if(next=='\n')break;line.append((char)next);}
     String response=line.toString().trim();if(!response.matches("HTTP/1\\.[01] [0-9]{3}.*"))throw new java.io.IOException("Invalid HTTP status");return Integer.parseInt(response.substring(9,12));
    }
   }finally{deadline.cancel(false);}
  }
 }
}
