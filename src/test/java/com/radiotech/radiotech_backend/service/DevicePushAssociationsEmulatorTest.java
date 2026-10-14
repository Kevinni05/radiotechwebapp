package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.*;
import com.google.cloud.firestore.FirestoreOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="FIRESTORE_EMULATOR_HOST",matches=".+")
class DevicePushAssociationsEmulatorTest {
    @Test void rotationAndRemoteRevocationPreserveTheOtherPhoneAndRejectForeignDevices() throws Exception {
        String tenant="device-push-"+UUID.randomUUID(), operator="op-"+UUID.randomUUID();
        String first="a".repeat(64), second="b".repeat(64), foreign="c".repeat(64);
        try(var db=FirestoreOptions.newBuilder().setProjectId("demo-radiotech").setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(new AccessToken("emulator",new Date(Long.MAX_VALUE)))).build().getService()) {
            db.collection("operators").document(operator).set(Map.of("tenantId",tenant,"firebaseUid","user-a","fcmTokens",List.of())).get();
            for(String id:List.of(first,second,foreign)) db.collection("pro_devices").document(id).set(
                Map.of("tenantId",tenant,"uid",id.equals(foreign)?"user-b":"user-a","status","ACTIVE")).get();
            DevicePushAssociations.change(db,operator,first,"old-token",tenant,"user-a",true);
            DevicePushAssociations.change(db,operator,second,"other-phone",tenant,"user-a",true);
            // Exercise the actual singular mobile HTTP route, not the plural management controller.
            var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "user-a", null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERATOR")));
            authentication.setDetails(new com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails("user-a",null,null,tenant));
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
            var operators = mock(OperatorService.class);
            var profile = new com.radiotech.radiotech_backend.model.Operator();profile.setId(operator);
            when(operators.getByFirebaseUid("user-a")).thenReturn(profile);
            var identity = mock(com.google.firebase.auth.FirebaseToken.class);
            when(identity.getClaims()).thenReturn(Map.of("radioDeviceId",first));
            var controller = new com.radiotech.radiotech_backend.controller.OperatorMobileController(
                mock(TaskService.class),mock(MaintenanceReportService.class),operators,
                mock(com.radiotech.radiotech_backend.security.TenantInvitationService.class));
            var http = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
            try(var database = mockStatic(com.google.firebase.cloud.FirestoreClient.class)) {
                database.when(com.google.firebase.cloud.FirestoreClient::getFirestore).thenReturn(db);
                http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/operator/me/fcm-token")
                    .requestAttr("firebaseUid","user-a").requestAttr("firebaseToken",identity)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"fcmToken\":\"new-token\"}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
            } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
            assertEquals(Set.of("new-token","other-phone"),new HashSet<>((List<?>)db.collection("operators").document(operator).get().get().get("fcmTokens")));
            assertThrows(SecurityException.class,()->DevicePushAssociations.change(db,operator,foreign,"foreign-token",tenant,"user-a",true));
            var ref=db.collection("pro_devices").document(first);
            db.runTransaction(tx->{var device=tx.get(ref).get();DevicePushAssociations.removeRevokedDevice(tx,db,device);tx.update(ref,"status","REVOKED");return true;}).get();
            assertEquals(List.of("other-phone"),db.collection("operators").document(operator).get().get().get("fcmTokens"));
            assertThrows(SecurityException.class,()->DevicePushAssociations.change(db,operator,first,"revoked-token",tenant,"user-a",true));
        }
    }
}
