package com.radiotech.radiotech_backend.ops;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

/** Host-declared revision. This is release traceability, not an attestation of artifact bytes. */
public final class ReleaseMetadata {
    private ReleaseMetadata() {}

    public static Map<String, String> current() { return from(System.getenv()); }

    public static Map<String, String> from(Map<String, String> environment) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("apiVersion", "1");
        String render = environment.get("RENDER_GIT_COMMIT");
        String declared = render != null && !render.isBlank() ? render : environment.get("RADIOTECH_RELEASE_REVISION");
        boolean valid = declared != null && declared.matches("(?i)[a-f0-9]{40}");
        result.put("sourceRevision", valid ? declared.toLowerCase(Locale.ROOT) : "UNKNOWN");
        result.put("revisionSource", valid ? (render != null && !render.isBlank() ? "RENDER_HOST" : "HOST_CONFIGURATION") : "UNVERIFIED");
        return result;
    }
}
