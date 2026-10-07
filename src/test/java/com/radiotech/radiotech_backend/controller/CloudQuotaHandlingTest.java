package com.radiotech.radiotech_backend.controller;
import com.radiotech.radiotech_backend.exception.*;
import com.radiotech.radiotech_backend.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CloudQuotaHandlingTest {
 @Test void quotaFailureIsUnavailableRatherThanInvalidCredentials() throws Exception {
  var auth=mock(AuthService.class);var quota=new java.util.concurrent.ExecutionException(io.grpc.Status.RESOURCE_EXHAUSTED.withDescription("Quota exceeded").asRuntimeException());
  when(auth.loginWithQrToken("test")).thenThrow(quota);
  var response=new AuthController(auth,mock(OperatorService.class)).qrLogin(java.util.Map.of("qrToken","test"));
  assertEquals(HttpStatus.SERVICE_UNAVAILABLE,response.getStatusCode());
  assertEquals(CloudQuota.MESSAGE,((java.util.Map<?,?>)response.getBody()).get("message"));
  assertEquals(HttpStatus.SERVICE_UNAVAILABLE,new GlobalExceptionHandler().handleException(quota).getStatusCode());
  assertFalse(CloudQuota.exhausted(new IllegalArgumentException("QR scaduto")));
 }
}
