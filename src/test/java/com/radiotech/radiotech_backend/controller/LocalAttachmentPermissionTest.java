package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import com.radiotech.radiotech_backend.service.LocalAttachmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalAttachmentPermissionTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void authenticate(String role) {
        var auth = new UsernamePasswordAuthenticationToken("operator-a", null, List.of(new SimpleGrantedAuthority("ROLE_"+role)));
        auth.setDetails(new FirebaseAuthenticationDetails("operator-a", "operator@example.test", "Operator", "tenant-a"));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
    @Test void operatorCanConfigureUploadAndReadOnlyOwnFiles() throws Exception {
        authenticate("OPERATOR"); var files=mock(LocalAttachmentService.class); when(files.enabled()).thenReturn(true);
        var controller=new LocalAttachmentController(files); assertEquals("LOCAL",controller.config().get("mode"));
        var request=new MockHttpServletRequest();request.setContent(new byte[]{1,2,3});
        controller.upload("report-key","report.pdf",request);
        verify(files).save(eq("tenant-a"),eq("operator-a"),eq("report-key"),eq("report.pdf"),any(byte[].class));
        when(files.read("file-id","tenant-a","operator-a",false)).thenReturn(Map.of("name","report.pdf"));
        assertEquals("report.pdf",controller.download("file-id").get("name"));
        verify(files).read("file-id","tenant-a","operator-a",false);
    }
    @Test void viewerCanReadButCannotUploadAndAnonymousHasNoAccess() {
        var controller=new LocalAttachmentController(mock(LocalAttachmentService.class));authenticate("VIEWER");
        assertDoesNotThrow(controller::config);
        assertThrows(SecurityException.class,()->controller.upload("key","report.pdf",new MockHttpServletRequest()));
        SecurityContextHolder.clearContext();assertThrows(SecurityException.class,controller::config);
    }
}
