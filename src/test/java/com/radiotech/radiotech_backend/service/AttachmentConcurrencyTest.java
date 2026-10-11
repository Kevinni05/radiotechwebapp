package com.radiotech.radiotech_backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AttachmentConcurrencyTest {
    @Test void capacityIsReservedBeforeBodyAllocationAndReleasedExactlyOnceEvenOnFailure() throws Exception {
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of("radiotech.attachments.max-concurrent-uploads", 2)));
        var files = new LocalAttachmentService(env);
        var first = files.reserveUpload(); var second = files.reserveUpload();
        assertEquals(429, assertThrows(ResponseStatusException.class, files::reserveUpload).getStatusCode().value());
        first.close(); first.close();
        try (var third = files.reserveUpload()) {
            assertThrows(ResponseStatusException.class, files::reserveUpload);
        }
        second.close();
        try (var a = files.reserveUpload(); var b = files.reserveUpload()) {
            assertThrows(ResponseStatusException.class, files::reserveUpload);
        }
    }
}
