package com.radiotech.radiotech_backend.config;

import com.radiotech.radiotech_backend.service.TenantDashboardCache;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import static org.mockito.Mockito.*;

class DashboardCacheInvalidationTest {
    @Test
    void successfulAndPartiallyFailedWritesInvalidateOnlyTheAuthenticatedTenant() throws Exception {
        TenantDashboardCache cache = mock(TenantDashboardCache.class);
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class);
        when(registry.addInterceptor(any())).thenReturn(registration);
        new WebConfig(cache).addInterceptors(registry);
        ArgumentCaptor<HandlerInterceptor> capture = ArgumentCaptor.forClass(HandlerInterceptor.class);
        verify(registry).addInterceptor(capture.capture());
        verify(registration).addPathPatterns("/api/**");
        var interceptor = capture.getValue();
        var request = new MockHttpServletRequest("POST", "/api/v1/tasks");
        request.setAttribute("tenantId", "authenticated-tenant");
        request.addHeader("X-Tenant-Id", "untrusted-tenant");
        var response = new MockHttpServletResponse();
        interceptor.afterCompletion(request, response, new Object(), null);
        response.setStatus(503);
        interceptor.afterCompletion(request, response, new Object(), new IllegalStateException("Partial write"));
        verify(cache, times(2)).invalidate("authenticated-tenant");
        request.setMethod("GET");
        interceptor.afterCompletion(request, response, new Object(), null);
        request.setMethod("DELETE"); request.removeAttribute("tenantId");
        interceptor.afterCompletion(request, response, new Object(), null);
        verifyNoMoreInteractions(cache);
    }
}
