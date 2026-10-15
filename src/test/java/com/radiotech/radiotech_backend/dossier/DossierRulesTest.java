package com.radiotech.radiotech_backend.dossier;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class DossierRulesTest {
  @Test
  void thresholdsAreNumericAndValidated() {
    var d =
        new HashMap<String, Object>(
            Map.of(
                "metric",
                "ROS",
                "value",
                2.1,
                "low",
                1,
                "high",
                2,
                "measuredAt",
                "2025-01-01T12:00:00Z",
                "actor",
                "spoofed"));
    var row = DossierRules.canonical("measurements", d);
    assertEquals(true, row.get("outsideThreshold"));
    assertEquals("MANUAL", row.get("source"));
    assertFalse(row.containsKey("actor"));
    d.put("low", 3);
    assertThrows(IllegalArgumentException.class, () -> DossierRules.canonical("measurements", d));
    d.put("value", Double.NaN);
    assertThrows(IllegalArgumentException.class, () -> DossierRules.canonical("measurements", d));
  }

  @Test
  void annotationsCannotEscapeImageOrReplaceOriginal() {
    var mark =
        new HashMap<String, Object>(
            Map.of("type", "ARROW", "x", 0.1, "y", 0.2, "x2", 0.8, "y2", 0.9));
    var photo =
        new HashMap<String, Object>(
            Map.of("title", "Connettore", "fileId", "a".repeat(64), "annotations", List.of(mark)));
    assertEquals("a".repeat(64), DossierRules.canonical("photos", photo).get("fileId"));
    mark.put("x", 1.1);
    assertThrows(IllegalArgumentException.class, () -> DossierRules.canonical("photos", photo));
  }

  @Test
  void sharedOutputContainsOnlyExplicitSectionsAndNoInternalIdentity() {
    var d = new HashMap<String, Object>();
    d.put("name", "Antenna A");
    d.put("access", Map.of("keyInstructions", "RISERVATO"));
    d.put("tenantId", "private");
    d.put("proposals", List.of(Map.of("reason", "private")));
    d.put(
        "configuration",
        List.of(
            Map.of("label", "Rev1", "values", Map.of("frequenza", "100"), "actor", "private-uid")));
    var shared = DossierRules.publicSnapshot(d, List.of("configuration"));
    assertFalse(shared.containsKey("access"));
    assertFalse(shared.containsKey("proposals"));
    assertFalse(shared.containsKey("tenantId"));
    assertFalse(DossierRules.rows(shared, "configuration").getFirst().containsKey("actor"));
    assertThrows(
        IllegalArgumentException.class, () -> DossierRules.publicSnapshot(d, List.of("proposals")));
  }

  @Test
  void sharedResolvedItemsKeepDecisionDetailsWithoutInternalReviewerIdentity() {
    for (String section : List.of("issues", "dependencies")) {
      var item =
          Map.<String, Object>of(
              "id",
              "entry",
              "title",
              "Verifica impianto",
              "actor",
              "operator-private",
              "resolvedBy",
              "manager-private",
              "resolvedAt",
              "2026-10-15T08:00:00Z",
              "resolutionReason",
              "Verifica completata",
              "status",
              "RESOLVED");
      var source = Map.<String, Object>of("name", "Antenna A", section, List.of(item));
      var shared = DossierRules.publicSnapshot(source, List.of(section));
      var published = DossierRules.rows(shared, section).getFirst();
      assertFalse(published.containsKey("actor"));
      assertFalse(published.containsKey("resolvedBy"));
      assertEquals("Verifica completata", published.get("resolutionReason"));
      assertEquals("2026-10-15T08:00:00Z", published.get("resolvedAt"));
      assertEquals("manager-private", item.get("resolvedBy"));
    }
  }

  @Test
  void unknownAndOversizedInputsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> DossierRules.id("../other"));
    assertThrows(
        IllegalArgumentException.class, () -> DossierRules.canonical("tenantId", Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> DossierRules.strings(Map.of("nested.field", "x"), 60));
  }

  @Test
  void onlyParticipantsOrTenantManagerCanReadPairedSession() {
    var s = Map.<String, Object>of("tenantId", "t", "uidA", "a", "uidB", "b");
    assertDoesNotThrow(() -> DossierService.sessionAccess(s, "t", "a", false));
    assertThrows(
        SecurityException.class, () -> DossierService.sessionAccess(s, "other", "a", true));
    assertThrows(SecurityException.class, () -> DossierService.sessionAccess(s, "t", "c", false));
  }
}
