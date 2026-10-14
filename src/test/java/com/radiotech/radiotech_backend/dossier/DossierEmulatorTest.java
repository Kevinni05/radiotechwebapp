package com.radiotech.radiotech_backend.dossier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.*;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.model.*;
import com.radiotech.radiotech_backend.security.*;
import com.radiotech.radiotech_backend.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="FIRESTORE_EMULATOR_HOST",matches=".+")
class DossierEmulatorTest {
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 void identity(String user,String role,String tenant){var a=new UsernamePasswordAuthenticationToken(user,null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));a.setDetails(new FirebaseAuthenticationDetails(user,"qa@example.test","QA",tenant));SecurityContextHolder.getContext().setAuthentication(a);}
 Firestore database(){return FirestoreOptions.newBuilder().setProjectId("demo-radiotech").setHost(System.getenv("FIRESTORE_EMULATOR_HOST")).setCredentials(GoogleCredentials.create(new AccessToken("emulator",new Date(Long.MAX_VALUE)))).build().getService();}
 Antenna antenna(String id,String tenant){var a=new Antenna();a.setId(id);a.setTenantId(tenant);a.setName(id);return a;}
 DossierService service(Firestore db,String tenant)throws Exception {var assets=mock(AntennaService.class);when(assets.getById(anyString())).thenAnswer(invocation->{String id=invocation.getArgument(0);if(!tenant.equals(SecurityContextAccessor.currentTenantId()))throw new SecurityException("Tenant");return antenna(id,tenant);});var operators=mock(OperatorService.class);for(String user:List.of("a","b")){var o=new Operator();o.setId(user);o.setFirebaseUid(user);o.setTenantId(tenant);o.setStatus("ATTIVO");o.setFullName(user);when(operators.getById(user)).thenReturn(o);}db.collection("antennas").document("asset-a").set(Map.of("tenantId",tenant)).get();db.collection("antennas").document("asset-b").set(Map.of("tenantId",tenant)).get();return new DossierService(assets,mock(LocalAttachmentService.class),operators,new ObjectMapper());}
 @Test void revisionsCorrectionsAndRetriesKeepOriginalEvidence()throws Exception{
  String t="dossier-"+UUID.randomUUID();identity("manager","ADMIN",t);
  try(var db=database();var firebase=mockStatic(FirestoreClient.class)){firebase.when(FirestoreClient::getFirestore).thenReturn(db);var s=service(db,t);
   var initial=new DossierService.Change(0,"access-one","access",Map.of("address","Prima"));s.change("asset-a",initial);s.change("asset-a",initial);assertEquals(1L,s.get("asset-a").get("version"));
   identity("a","OPERATOR",t);assertThrows(SecurityException.class,()->s.change("asset-a",new DossierService.Change(1,"forbidden","access",Map.of("address","Spoof"))));
   s.change("asset-a",new DossierService.Change(1,"proposal-one","proposals",Map.of("target","access","field","address","value","Dopo","reason","Verificato in loco")));
   identity("manager","ADMIN",t);assertThrows(RuntimeException.class,()->s.review("asset-a",new DossierService.Review(1,"review-stale","proposal-one",true,"Verificato")));
   s.review("asset-a",new DossierService.Review(2,"review-one","proposal-one",true,"Confermato dal referente"));var result=s.get("asset-a");assertEquals("Dopo",DossierRules.map(result.get("access")).get("address"));var proposal=DossierRules.rows(result,"proposals").getFirst();assertEquals("Prima",proposal.get("before"));assertEquals("APPROVED",proposal.get("status"));assertEquals(3,db.collection("antennaDossiers").document(DossierService.digest(t+":asset-a")).collection("audit").get().get().size());
  }
 }
 @Test void publicSnapshotNeverIncludesUnselectedSectionsAndRevocationIsImmediate()throws Exception{
  String t="share-"+UUID.randomUUID();identity("manager","ADMIN",t);
  try(var db=database();var firebase=mockStatic(FirestoreClient.class)){firebase.when(FirestoreClient::getFirestore).thenReturn(db);var s=service(db,t);s.change("asset-a",new DossierService.Change(0,"access","access",Map.of("keyInstructions","SEGRETO")));
   var share=s.createShare("asset-a",new DossierService.Share(List.of("identity"),1));String token=share.get("token").toString();var snapshot=DossierRules.map(s.publicRead(new DossierService.PublicRead(token,null)).get("snapshot"));assertFalse(snapshot.containsKey("access"));assertFalse(snapshot.containsKey("uid"));assertThrows(SecurityException.class,()->s.publicRead(new DossierService.PublicRead(token,"a".repeat(64))));assertEquals(2L,s.shares("asset-a").getFirst().get("accessCount"));
   s.revoke("asset-a",share.get("id").toString());assertThrows(SecurityException.class,()->s.publicRead(new DossierService.PublicRead(token,null)));
  }
 }
 @Test void pairedTrialRequiresOwnSideAndBothResultsBeforeClosing()throws Exception{
  String t="pair-"+UUID.randomUUID();identity("manager","ADMIN",t);
  try(var db=database();var firebase=mockStatic(FirestoreClient.class)){firebase.when(FirestoreClient::getFirestore).thenReturn(db);var s=service(db,t);String key=s.createSession("asset-a",new DossierService.SessionCreate("asset-b","a","b","Ponte A B","create")).get("id").toString();
   identity("a","OPERATOR",t);s.trial(key,new DossierService.Trial(0,"trial-one","CREATE_TRIAL",null,null,"Potenza nominale",Map.of("potenza","10W"),null,null,null,null));
   assertThrows(SecurityException.class,()->s.trial(key,new DossierService.Trial(1,"wrong-side","RESULT","trial-one","B",null,null,"RSSI","dBm",-50d,"")));
   var resultA=new DossierService.Trial(1,"result-a","RESULT","trial-one","A",null,null,"RSSI","dBm",-52d,"");s.trial(key,resultA);s.trial(key,resultA);
   assertThrows(IllegalArgumentException.class,()->s.trial(key,new DossierService.Trial(2,"close-early","CLOSE",null,null,null,null,null,null,null,"Prova")));
   identity("b","OPERATOR",t);s.trial(key,new DossierService.Trial(2,"result-b","RESULT","trial-one","B",null,null,"RSSI","dBm",-49d,""));var done=s.trial(key,new DossierService.Trial(3,"close","CLOSE",null,null,null,null,null,null,null,"Entrambi gli estremi verificati"));assertEquals("CLOSED",done.get("state"));
   identity("stranger","OPERATOR",t);assertThrows(SecurityException.class,()->s.session(key));identity("a","OPERATOR","other");assertThrows(SecurityException.class,()->s.session(key));
  }
 }
}
