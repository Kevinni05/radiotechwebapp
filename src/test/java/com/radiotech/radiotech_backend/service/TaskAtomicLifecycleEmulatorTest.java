package com.radiotech.radiotech_backend.service;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.model.Task;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="FIRESTORE_EMULATOR_HOST",matches=".+")
class TaskAtomicLifecycleEmulatorTest {
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 private Firestore db(){return FirestoreOptions.getDefaultInstance().toBuilder().setProjectId("demo-radiotech")
  .setEmulatorHost(System.getenv("FIRESTORE_EMULATOR_HOST")).build().getService();}
 private void authenticate(String tenant,String uid,String role){
  var authentication=new UsernamePasswordAuthenticationToken(uid,null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));
  authentication.setDetails(new FirebaseAuthenticationDetails(uid,"actor@example.test","Actor",tenant));
  SecurityContextHolder.getContext().setAuthentication(authentication);
 }
 private TaskService service(AuditService audit){
  var service=new TaskService(mock(OperatorService.class),audit,mock(NotificationService.class));
  ReflectionTestUtils.setField(service,"geofenceRadiusMeters",250.0);return service;
 }
 private String seed(Firestore db,String tenant,String status)throws Exception{
  String asset=UUID.randomUUID().toString(), task=UUID.randomUUID().toString();
  db.collection("antennas").document(asset).set(Map.of("tenantId",tenant,"lat",41.1171,"lng",16.8719)).get();
  db.collection("tasks").document(task).set(Map.of("tenantId",tenant,"operatorId","operator-a",
   "operatorFirebaseUid","operator-a","antennaId",asset,"status",status)).get();return task;
 }
 @Test void lifecycleCommitsAuditOnceAndGenericCompletionCannotBypassGps()throws Exception{
  String tenant="atomic-task-"+UUID.randomUUID();authenticate(tenant,"operator-a","OPERATOR");
  try(Firestore db=db();var client=mockStatic(FirestoreClient.class)){
   client.when(FirestoreClient::getFirestore).thenReturn(db);
   String task=seed(db,tenant,"EN_ROUTE");AuditService audit=mock(AuditService.class);TaskService service=service(audit);
   assertEquals("CHECKED_IN",service.checkInTask(task,"operator-a","operator-a",41.1171,16.8719).getStatus());
   service.checkInTask(task,"operator-a","operator-a",41.1171,16.8719);
   service.updateStatus(task,"IN_PROGRESS","start-one");
   assertThrows(IllegalArgumentException.class,()->service.updateStatus(task,"COMPLETED","no-gps"));
   assertEquals("IN_PROGRESS",service.getById(task).getStatus());
   service.completeTaskWithLocation(task,"operator-a","operator-a",41.1171,16.8719);
   service.completeTaskWithLocation(task,"operator-a","operator-a",41.1171,16.8719);
   var events=db.collection("auditLogs").whereEqualTo("tenantId",tenant).get().get().getDocuments();
   assertEquals(3,events.size());
   assertEquals(java.util.Set.of("TASK_CHECKED_IN","TASK_STATUS_CHANGED","TASK_CHECKED_OUT"),
    events.stream().map(event->event.getString("action")).collect(java.util.stream.Collectors.toSet()));
   for(var event:events){assertEquals("operator-a",event.getString("actor"));assertEquals("SUCCESS",event.getString("result"));}
   assertEquals(1,db.collection("apiIdempotencyKeys").whereEqualTo("tenantId",tenant).get().get().size());
   authenticate(tenant,"manager-a","NETWORK_MANAGER");service.deleteTask(task);
   assertFalse(db.collection("tasks").document(task).get().get().exists());
   var deletions=db.collection("auditLogs").whereEqualTo("tenantId",tenant).whereEqualTo("action","TASK_DELETED").get().get().getDocuments();
   assertEquals(1,deletions.size());assertEquals("manager-a",deletions.getFirst().getString("actor"));
   verifyNoInteractions(audit);
  }
 }
 @Test void assignmentChangeDuringGpsRequestCannotBeCommittedByTheOldOperator()throws Exception{
  String tenant="race-task-"+UUID.randomUUID();authenticate(tenant,"operator-a","OPERATOR");
  try(Firestore db=db();var client=mockStatic(FirestoreClient.class)){
   client.when(FirestoreClient::getFirestore).thenReturn(db);
   for(String initial:List.of("EN_ROUTE","IN_PROGRESS")){
    String task=seed(db,tenant,initial);TaskService service=spy(service(mock(AuditService.class)));
    AtomicBoolean changed=new AtomicBoolean();
    doAnswer(invocation->{
     Task before=(Task)invocation.callRealMethod();
     if(changed.compareAndSet(false,true))db.collection("tasks").document(task)
      .update("operatorId","operator-b","operatorFirebaseUid","operator-b").get();
     return before;
    }).when(service).getById(task);
    if(initial.equals("EN_ROUTE"))assertThrows(SecurityException.class,
     ()->service.checkInTask(task,"operator-a","operator-a",41.1171,16.8719));
    else assertThrows(SecurityException.class,
     ()->service.completeTaskWithLocation(task,"operator-a","operator-a",41.1171,16.8719));
    assertEquals(initial,db.collection("tasks").document(task).get().get().getString("status"));
    assertEquals(0,db.collection("auditLogs").whereEqualTo("resourceId",task).get().get().size());
   }
  }
 }
}
