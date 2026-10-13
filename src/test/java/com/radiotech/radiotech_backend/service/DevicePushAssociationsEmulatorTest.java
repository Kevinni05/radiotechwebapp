package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.*;
import com.google.cloud.firestore.FirestoreOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

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
            DevicePushAssociations.change(db,operator,first,"new-token",tenant,"user-a",true);
            assertEquals(Set.of("new-token","other-phone"),new HashSet<>((List<?>)db.collection("operators").document(operator).get().get().get("fcmTokens")));
            assertThrows(SecurityException.class,()->DevicePushAssociations.change(db,operator,foreign,"foreign-token",tenant,"user-a",true));
            var ref=db.collection("pro_devices").document(first);
            db.runTransaction(tx->{var device=tx.get(ref).get();DevicePushAssociations.removeRevokedDevice(tx,db,device);tx.update(ref,"status","REVOKED");return true;}).get();
            assertEquals(List.of("other-phone"),db.collection("operators").document(operator).get().get().get("fcmTokens"));
            assertThrows(SecurityException.class,()->DevicePushAssociations.change(db,operator,first,"revoked-token",tenant,"user-a",true));
        }
    }
}
