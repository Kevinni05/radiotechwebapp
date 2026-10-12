package com.radiotech.radiotech_backend.service;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;
@EnabledIfEnvironmentVariable(named="FIRESTORE_EMULATOR_HOST",matches=".+")
class PrivateDistributionEmulatorTest {
 @Test void packagesRequireTenantEntitlementAndVerifiedBytes()throws Exception{
  String tenant="distribution-qa-"+UUID.randomUUID();byte[] bytes=new byte[8*512*1024+17];Arrays.fill(bytes,(byte)7);String sha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
  try(var db=FirestoreOptions.getDefaultInstance().toBuilder().setProjectId("demo-radiotech").setEmulatorHost(System.getenv("FIRESTORE_EMULATOR_HOST")).build().getService();var client=mockStatic(FirestoreClient.class)){
   client.when(FirestoreClient::getFirestore).thenReturn(db);var service=new PrivateDistributionService();String filename="RadioTech-1.5.3-11001.apk";
   db.collection("privateAppReleases").document(tenant).set(Map.of("tenantId",tenant,"available",true,"filename",filename,"sha256",sha,"signingSha256","a".repeat(64),"size",bytes.length,"versionCode",11001L,"versionName","1.5.3","applicationId","com.example.gestionale_radio")).get();
   var pkg=db.collection("privateAppPackages").document(sha);pkg.set(Map.of("state","READY","sha256",sha,"size",bytes.length,"chunkCount",9L)).get();for(int index=0;index<9;index++)pkg.collection("chunks").document(String.format(Locale.ROOT,"%06d",index)).set(Map.of("data",Blob.fromBytes(Arrays.copyOfRange(bytes,index*512*1024,Math.min(bytes.length,(index+1)*512*1024))))).get();
   assertEquals(false,service.latest("foreign").get("available"));assertThrows(IllegalArgumentException.class,()->service.prepare("foreign",filename));
   java.nio.file.Path stage;try(var download=service.prepare(tenant,filename)){stage=download.file();assertArrayEquals(bytes,Files.readAllBytes(stage));}assertFalse(Files.exists(stage));
   pkg.collection("chunks").document("000000").set(Map.of("data",Blob.fromBytes("broken bytes same length".getBytes()))).get();assertThrows(IllegalStateException.class,()->service.prepare(tenant,filename));
   db.collection("privateAppReleases").document(tenant).update("tenantId","foreign").get();assertThrows(SecurityException.class,()->service.latest(tenant));
  }
 }
}
