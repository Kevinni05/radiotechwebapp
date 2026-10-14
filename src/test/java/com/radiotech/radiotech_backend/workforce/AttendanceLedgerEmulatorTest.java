package com.radiotech.radiotech_backend.workforce;

import com.google.auth.oauth2.*;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.dto.WorkforceRequests;
import com.radiotech.radiotech_backend.security.*;
import com.radiotech.radiotech_backend.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="FIRESTORE_EMULATOR_HOST",matches=".+")
class AttendanceLedgerEmulatorTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    void identity(String uid,String role,String tenant){var auth=new UsernamePasswordAuthenticationToken(uid,null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));auth.setDetails(new FirebaseAuthenticationDetails(uid,"test@example.test","Mario",tenant));SecurityContextHolder.getContext().setAuthentication(auth);}
    Firestore database(){return FirestoreOptions.newBuilder().setProjectId("demo-radiotech").setHost(System.getenv("FIRESTORE_EMULATOR_HOST")).setCredentials(GoogleCredentials.create(new AccessToken("emulator",new Date(Long.MAX_VALUE)))).build().getService();}
    @Test void ledgerKeepsCompletedSessionsAndIdempotentRetriesDoNotDuplicateEvents() throws Exception {
        String tenant="ledger-"+UUID.randomUUID();identity("op-a","OPERATOR",tenant);
        try(var db=database();var firebase=mockStatic(FirestoreClient.class)) {
            firebase.when(FirestoreClient::getFirestore).thenReturn(db);var workforce=new WorkforceService(mock(AuditService.class));
            var endAt=Instant.now().minusSeconds(30);var began=endAt.minusSeconds(3660);
            var start=new WorkforceRequests.Shift("START","READY",0L,UUID.randomUUID().toString(),began.toString());
            var started=workforce.changeShift(start);String id=started.get("sessionId").toString();
            workforce.changeShift(start);
            var end=new WorkforceRequests.Shift("END","READY",1L,UUID.randomUUID().toString(),endAt.toString());workforce.changeShift(end);workforce.changeShift(end);
            assertEquals(2,db.collection("workforceTimeEvents").whereEqualTo("tenantId",tenant).get().get().size());
            var row=db.collection("workforceAttendance").document(id).get().get();assertEquals(61L,row.getLong("workMinutes"));
            workforce.changeShift(new WorkforceRequests.Shift("START","READY",2L,UUID.randomUUID().toString()));
            assertEquals(2,db.collection("workforceAttendance").whereEqualTo("tenantId",tenant).get().get().size());
            var attendance=new AttendanceService();assertEquals(2,attendance.report(YearMonth.now(ZoneId.of("Europe/Rome")).toString(),null,true).get("sessions") instanceof List<?> l?l.size():0);
            assertThrows(SecurityException.class,()->attendance.report(YearMonth.now().toString(),null,false));
            identity("op-b","OPERATOR",tenant);assertThrows(SecurityException.class,()->attendance.events(id));
            identity("manager","ADMIN",tenant+"-other");assertThrows(SecurityException.class,()->attendance.review(id,new AttendanceService.Review("APPROVE","Verificato",1)));
        }
    }
    @Test void correctionApprovalPreservesOriginalsAndAuditAndRejectsStaleVersions() throws Exception {
        String tenant="correction-"+UUID.randomUUID();identity("op-a","OPERATOR",tenant);
        try(var db=database();var firebase=mockStatic(FirestoreClient.class)) {
            firebase.when(FirestoreClient::getFirestore).thenReturn(db);var workforce=new WorkforceService(mock(AuditService.class));var attendance=new AttendanceService();
            var now=Instant.now().minusSeconds(60);String startAt=now.minusSeconds(3600).toString();
            String id=workforce.changeShift(new WorkforceRequests.Shift("START","READY",0L,UUID.randomUUID().toString(),startAt)).get("sessionId").toString();
            workforce.changeShift(new WorkforceRequests.Shift("END","READY",1L,UUID.randomUUID().toString(),now.toString()));
            var correction=new AttendanceService.Correction(id,now.minusSeconds(7200).toString(),now.toString(),15,"Entrata dimenticata",2);
            attendance.correction(correction);
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->attendance.correction(correction));
            identity("manager","ADMIN",tenant);var approved=attendance.review(id,new AttendanceService.Review("APPROVE","Confermato dal responsabile",3));
            assertEquals(startAt,approved.get("startedAt"));assertEquals(105L,((Map<?,?>)approved.get("approved")).get("workMinutes"));
            assertEquals(2,db.collection("workforceAttendanceAudit").whereEqualTo("tenantId",tenant).get().get().size());
        }
    }
    @Test void conflictedQueueTransferIsIdempotentAndManagerReconciliationIsAudited() throws Exception {
        String tenant="recovery-"+UUID.randomUUID();identity("op-a","OPERATOR",tenant);
        try(var db=database();var firebase=mockStatic(FirestoreClient.class)) {
            firebase.when(FirestoreClient::getFirestore).thenReturn(db);var service=new ClockRecoveryService();Instant end=Instant.now().minusSeconds(60),start=end.minusSeconds(3600);
            var request=new ClockRecoveryService.Request(List.of(new WorkforceRequests.Shift("START","READY",0L,UUID.randomUUID().toString(),start.toString()),new WorkforceRequests.Shift("END","READY",1L,UUID.randomUUID().toString(),end.toString())));
            var archived=service.archive(request);assertEquals(archived.get("id"),service.archive(request).get("id"));String id=archived.get("id").toString();
            assertEquals(1,db.collection("workforceClockRecoveries").whereEqualTo("tenantId",tenant).get().get().size());assertThrows(SecurityException.class,service::pending);
            identity("manager","ADMIN",tenant);var interval=new ClockRecoveryService.Interval(start.toString(),end.toString(),0,"Orari verificati",0);
            var recorded=service.interval(id,interval);assertEquals("APPROVED",db.collection("workforceAttendance").document(recorded.get("id").toString()).get().get().getString("reviewStatus"));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.interval(id,interval));service.close(id,new ClockRecoveryService.Close("Tutti gli eventi verificati",1));
            assertEquals("RESOLVED",db.collection("workforceClockRecoveries").document(id).get().get().getString("status"));assertEquals(2,db.collection("workforceAttendanceAudit").whereEqualTo("tenantId",tenant).get().get().size());
        }
    }
    @Test void publicationChecksVersionsAndKeepsImmutableSnapshotWhenDraftChanges() throws Exception {
        String tenant="roster-"+UUID.randomUUID();identity("manager","ADMIN",tenant);
        try(var db=database();var firebase=mockStatic(FirestoreClient.class)) {
            firebase.when(FirestoreClient::getFirestore).thenReturn(db);var operators=mock(OperatorService.class);var op=new com.radiotech.radiotech_backend.model.Operator();op.setId("op-a");op.setTenantId(tenant);op.setStatus("ATTIVO");op.setFirebaseUid("uid-a");op.setFullName("Mario Rossi");when(operators.getById("op-a")).thenReturn(op);
            var service=new RosterService(operators,mock(NotificationService.class),new RosterSolver());
            var rules=new Roster.Rules("Europe/Rome",660,5,2400,10,2);var template=new Roster.Template("DAY","Diurno","08:00","16:00",0,1,List.of(1,2,3,4,5),"Lecce","",false);
            var member=new Roster.Member("op-a",0,12000,0,2400,5,false,List.of(1,2,3,4,5),List.of(),List.of(),List.of(),List.of());
            var c=new Roster.Config(rules,List.of(template),List.of(member),List.of(),0,"");service.save("2026-10",c);
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.generate("2026-10",new Roster.Version(0)));
            var generation=service.generate("2026-10",new Roster.Version(1));assertEquals(true,generation.get("generated"));service.publish("2026-10",new Roster.Version(2));
            var draft=service.draft("2026-10");var config=new Roster.Config(rules,List.of(template),List.of(member),List.of(),3,"Nuova bozza");service.save("2026-10",config);
            assertEquals(1L,RosterService.published(tenant,"2026-10").get("revision"));assertEquals(1,db.collection("workforcePlanVersions").whereEqualTo("tenantId",tenant).get().get().size());
            identity("uid-a","OPERATOR",tenant);var personal=service.personal("2026-10");assertFalse(personal.containsKey("notes"));assertEquals(22,RosterService.rows(personal,"slots").size());assertThrows(SecurityException.class,()->service.draft("2026-10"));
            identity("uid-b","OPERATOR",tenant);assertTrue(RosterService.rows(service.personal("2026-10"),"slots").isEmpty());
        }
    }
}
