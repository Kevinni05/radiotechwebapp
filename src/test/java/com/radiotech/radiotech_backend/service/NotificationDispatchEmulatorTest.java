package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.*;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.model.Operator;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="FIRESTORE_EMULATOR_HOST",matches=".+")
class NotificationDispatchEmulatorTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    private Operator operator(String id,String tenant){var value=new Operator();value.setId(id);value.setTenantId(tenant);value.setFirebaseUid("uid-"+id);value.setStatus("ATTIVO");return value;}
    @Test void dashboardReplayKeepsOneInboxPerRecipientAndNoDevicesIsExplicit() throws Exception {
        String tenant="dispatch-"+UUID.randomUUID();
        var actor=new UsernamePasswordAuthenticationToken("manager",null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        actor.setDetails(new FirebaseAuthenticationDetails("manager",null,"Manager",tenant));SecurityContextHolder.getContext().setAuthentication(actor);
        var operators=mock(OperatorService.class);var delivery=mock(NotificationService.class);
        when(operators.getById("a")).thenReturn(operator("a",tenant));when(operators.getById("b")).thenReturn(operator("b",tenant));
        try(var db=FirestoreOptions.newBuilder().setProjectId("demo-radiotech").setHost(System.getenv("FIRESTORE_EMULATOR_HOST")).setCredentials(GoogleCredentials.create(new AccessToken("emulator",new Date(Long.MAX_VALUE)))).build().getService();var mocked=mockStatic(FirestoreClient.class)) {
            mocked.when(FirestoreClient::getFirestore).thenReturn(db);
            doAnswer(call->{db.collection("notificationHistory").document(call.getArgument(0,String.class)).update("deliveryStatus","NO_TOKENS").get();return null;}).when(delivery).deliverRecorded(anyString());
            var service=new NotificationDispatchService(operators,delivery);
            var request=new LinkedHashMap<String,Object>(Map.of("operationId","same-operation","target","SELECTED","recipients",List.of("a","b","a"),"title","Avviso","message","Messaggio QA"));
            var first=service.dispatch(request);var second=service.dispatch(request);
            assertEquals(first.get("notificationIds"),second.get("notificationIds"));assertEquals("NO_TOKENS",first.get("deliveryStatus"));assertEquals(2L,first.get("recipientsWithoutDevices"));
            var records=db.collection("notificationHistory").whereEqualTo("tenantId",tenant).get().get().getDocuments();assertEquals(2,records.size());
            assertEquals(Set.of("uid-a","uid-b"),new HashSet<>(records.stream().map(item->item.getString("recipientUid")).toList()));
            request.put("message","Contenuto differente");assertThrows(ResponseStatusException.class,()->service.dispatch(request));
            when(operators.getById("b")).thenReturn(operator("b","foreign-tenant"));
            request.put("operationId","foreign-target");assertThrows(SecurityException.class,()->service.dispatch(request));
            assertEquals(2,db.collection("notificationHistory").whereEqualTo("tenantId",tenant).get().get().size());
        }
    }
}
