package com.radiotech.radiotech_backend;

import com.radiotech.radiotech_backend.ops.ReleaseMetadata;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseMetadataTest {
    @Test void identifiesHostRevisionWithoutInventingArtifactAttestation() {
        String revision = "a".repeat(40);
        var metadata = ReleaseMetadata.from(Map.of("RENDER_GIT_COMMIT", revision.toUpperCase(), "RADIOTECH_RELEASE_REVISION", "b".repeat(40)));
        assertEquals(revision, metadata.get("sourceRevision"));
        assertEquals("RENDER_HOST", metadata.get("revisionSource"));
        assertEquals("1", metadata.get("apiVersion"));
        assertEquals(3, metadata.size());
        assertEquals("HOST_CONFIGURATION", ReleaseMetadata.from(Map.of("RADIOTECH_RELEASE_REVISION", revision)).get("revisionSource"));
    }
    @Test void missingOrInvalidHostMetadataRemainsUnknownAndDoesNotExposeItsValue() {
        assertEquals("UNKNOWN", ReleaseMetadata.from(Map.of()).get("sourceRevision"));
        var invalid = ReleaseMetadata.from(Map.of("RENDER_GIT_COMMIT", "not-a-revision-sensitive-input", "RADIOTECH_RELEASE_REVISION", "a".repeat(40)));
        assertEquals("UNKNOWN", invalid.get("sourceRevision"));
        assertEquals("UNVERIFIED", invalid.get("revisionSource"));
        assertFalse(invalid.toString().contains("sensitive-input"));
    }
}
