package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.dto.TechnicianSkillRequest;
import com.radiotech.radiotech_backend.model.TechnicianSkillType;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class TechnicianSkillServiceEmulatorTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void skillsAreTenantScopedAuditedAndUpsertedBySkill() throws Exception {
        authenticate("tenant-a");
        try (Firestore firestore = emulatorFirestore();
                var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(firestore);
            firestore.collection("operators").document("skill-tech-a").set(Map.of(
                    "tenantId", "tenant-a", "fullName", "Tech A")).get();

            AuditService audit = mock(AuditService.class);
            TechnicianSkillService service = new TechnicianSkillService(audit);
            TechnicianSkillRequest request = request(3, "RF-Field", "2027-09-30", true);
            var created = service.upsert("skill-tech-a", TechnicianSkillType.RF, request);
            assertEquals(3, created.level());
            assertEquals("RF-Field", created.certification());
            assertTrue(created.authorized());

            var updated = service.upsert("skill-tech-a", TechnicianSkillType.RF,
                    request(4, "RF-Advanced", "2028-09-30", false));
            assertEquals(4, updated.level());
            assertFalse(updated.authorized());
            assertEquals(1, service.list("skill-tech-a").size());
            assertEquals("RF-Advanced", service.list("skill-tech-a").get(0).certification());

            service.delete("skill-tech-a", TechnicianSkillType.RF);
            assertTrue(service.list("skill-tech-a").isEmpty());
            verify(audit).record(org.mockito.ArgumentMatchers.eq("TECHNICIAN_SKILL_CREATED"),
                    org.mockito.ArgumentMatchers.eq("tenant-a"), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq("technicianSkill"),
                    org.mockito.ArgumentMatchers.eq("skill-tech-a:RF"), org.mockito.ArgumentMatchers.eq("SUCCESS"),
                    org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyMap());
            verify(audit).record(org.mockito.ArgumentMatchers.eq("TECHNICIAN_SKILL_UPDATED"),
                    org.mockito.ArgumentMatchers.eq("tenant-a"), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq("technicianSkill"),
                    org.mockito.ArgumentMatchers.eq("skill-tech-a:RF"), org.mockito.ArgumentMatchers.eq("SUCCESS"),
                    org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyMap());
            verify(audit).record(org.mockito.ArgumentMatchers.eq("TECHNICIAN_SKILL_DELETED"),
                    org.mockito.ArgumentMatchers.eq("tenant-a"), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq("technicianSkill"),
                    org.mockito.ArgumentMatchers.eq("skill-tech-a:RF"), org.mockito.ArgumentMatchers.eq("SUCCESS"),
                    org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyMap());
        }
    }

    @Test
    void otherTenantCannotReadOrMutateTechnicianSkills() throws Exception {
        authenticate("tenant-b");
        try (Firestore firestore = emulatorFirestore();
                var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(firestore);
            firestore.collection("operators").document("skill-tech-a").set(Map.of("tenantId", "tenant-a")).get();
            TechnicianSkillService service = new TechnicianSkillService(mock(AuditService.class));

            assertThrows(IllegalArgumentException.class, () -> service.list("skill-tech-a"));
            assertThrows(IllegalArgumentException.class, () -> service.upsert("skill-tech-a", TechnicianSkillType.RF,
                    request(1, "RF-1", "2027-09-30", true)));
            assertThrows(IllegalArgumentException.class, () -> service.delete("skill-tech-a", TechnicianSkillType.RF));
        }
    }

    @Test
    void mismatchedSkillOwnershipCannotBeReadUpdatedOrDeleted() throws Exception {
        authenticate("tenant-a");
        try (Firestore firestore = emulatorFirestore();
                var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(firestore);
            firestore.collection("operators").document("skill-corrupt-a").set(Map.of("tenantId", "tenant-a")).get();
            var skill = firestore.collection("operators").document("skill-corrupt-a")
                    .collection("skills").document("RF");
            skill.set(Map.of(
                    "tenantId", "tenant-b",
                    "operatorId", "skill-corrupt-a",
                    "skill", "RF",
                    "level", 2,
                    "certification", "RF-L2",
                    "expiration", "2027-09-30",
                    "authorized", true,
                    "updatedAt", "2026-10-01T00:00:00Z")).get();

            AuditService audit = mock(AuditService.class);
            TechnicianSkillService service = new TechnicianSkillService(audit);
            assertThrows(IllegalStateException.class, () -> service.list("skill-corrupt-a"));
            assertThrows(IllegalStateException.class, () -> service.upsert("skill-corrupt-a",
                    TechnicianSkillType.RF, request(3, "RF-L3", "2028-09-30", true)));
            assertThrows(IllegalStateException.class,
                    () -> service.delete("skill-corrupt-a", TechnicianSkillType.RF));
            assertEquals("tenant-b", skill.get().get().getString("tenantId"));
            org.mockito.Mockito.verifyNoInteractions(audit);
        }
    }

    private TechnicianSkillRequest request(int level, String certificate, String expiration, boolean authorized) {
        TechnicianSkillRequest request = new TechnicianSkillRequest();
        request.setLevel(level);
        request.setCertification(certificate);
        request.setExpiration(LocalDate.parse(expiration));
        request.setAuthorized(authorized);
        return request;
    }

    private void authenticate(String tenantId) {
        var authentication = new UsernamePasswordAuthenticationToken(
                "manager", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "manager", "manager@example.test", "Manager", tenantId));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Firestore emulatorFirestore() {
        return FirestoreOptions.newBuilder()
                .setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                .build().getService();
    }
}
