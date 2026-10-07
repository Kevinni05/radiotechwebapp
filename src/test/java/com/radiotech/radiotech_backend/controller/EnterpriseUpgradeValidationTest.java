package com.radiotech.radiotech_backend.controller;
import com.radiotech.radiotech_backend.model.Operator;
import com.radiotech.radiotech_backend.service.*;
import com.radiotech.radiotech_backend.security.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import java.util.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class EnterpriseUpgradeValidationTest {
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 void auth(String role){var a=new UsernamePasswordAuthenticationToken("uid-test",null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));a.setDetails(new FirebaseAuthenticationDetails("uid-test","fixture@example.test","Test","tenant-test"));SecurityContextHolder.getContext().setAuthentication(a);}
 Operator operator(){var o=new Operator();o.setId("op-test");o.setTenantId("tenant-test");o.setStatus("ATTIVO");o.setEmail("fixture@example.test");o.setQrCodeToken("AUTH_OP_fixture");o.setQrValidityMode("UNLIMITED");return o;}
 @Test void expiryHasExplicitUnlimitedAndRejectsMissingOrMalformed(){assertFalse(OperatorService.badgeExpired(null,"UNLIMITED"));assertTrue(OperatorService.badgeExpired(null,"DEFAULT"));assertTrue(OperatorService.badgeExpired("bad","FIXED"));assertTrue(OperatorService.badgeExpired("2000-01-01T00:00:00Z","FIXED"));assertFalse(OperatorService.badgeExpired("2099-01-01T00:00:00Z","FIXED"));}
 @Test void calendarRejectsImpossibleDaysAndPastReminders(){assertEquals("2028-02-29",CalendarController.validateDay("2028-02-29"));assertThrows(IllegalArgumentException.class,()->CalendarController.validateDay("2026-02-29"));assertThrows(IllegalArgumentException.class,()->CalendarController.validateReminder("2000-01-01T00:00:00Z"));assertThrows(IllegalArgumentException.class,()->CalendarController.validateReminder("2028-01-01T12:00"));assertNull(CalendarController.validateReminder(""));assertEquals("2099-01-01T12:00:00Z",CalendarController.validateReminder("2099-01-01T12:00:00Z"));}
 @Test void mobileAndWebProduceExactlyTheSameQrPayload()throws Exception{
  auth("ADMIN");var ops=mock(OperatorService.class);var qr=mock(QrCodeService.class);when(ops.getById("op-test")).thenReturn(operator());when(ops.getByFirebaseUid("uid-test")).thenReturn(operator());when(ops.personalBadge("op-test")).thenReturn(operator());when(qr.toDataUri(anyString())).thenReturn("data:image/png;base64,fixture");
  var web=new OperatorController(ops,qr,mock(TenantInvitationService.class));var mobile=new OperatorBadgeController(ops,qr,mock(RestClient.class));ReflectionTestUtils.setField(web,"publicBaseUrl","https://test.example/");ReflectionTestUtils.setField(mobile,"publicUrl","https://test.example/");assertEquals(200,web.badge("op-test",null).getStatusCode().value());assertEquals(200,mobile.mine().getStatusCode().value());var args=org.mockito.ArgumentCaptor.forClass(String.class);verify(qr,times(2)).toDataUri(args.capture());assertEquals(args.getAllValues().get(0),args.getAllValues().get(1));var json=tools.jackson.databind.json.JsonMapper.builder().build().readTree(args.getValue());assertEquals(2,json.get("version").asInt());assertEquals("https://test.example",json.get("serverUrl").asText());
 }
 @Test void viewerCannotChangeAnotherOperatorsQrOrPassword(){auth("VIEWER");var ops=mock(OperatorService.class);var c=new OperatorBadgeController(ops,mock(QrCodeService.class),mock(RestClient.class));assertEquals(403,c.validity("other",Map.of("mode","UNLIMITED")).getStatusCode().value());assertEquals(403,c.resetOther("other").getStatusCode().value());verifyNoInteractions(ops);}
 @Test void passwordResetUsesPersistedEmailAndRotatesAfterProviderSuccess()throws Exception {
  auth("OPERATOR");var ops=mock(OperatorService.class);when(ops.getByFirebaseUid("uid-test")).thenReturn(operator());var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();var c=new OperatorBadgeController(ops,mock(QrCodeService.class),builder.build());ReflectionTestUtils.setField(c,"apiKey","fixture-key");
  server.expect(requestTo("https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=fixture-key")).andExpect(content().json("{\"requestType\":\"PASSWORD_RESET\",\"email\":\"fixture@example.test\"}")).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));assertEquals(200,c.resetOwn().getStatusCode().value());verify(ops).regenerateQrToken("op-test");server.verify();
 }
 @Test void failedEmailDoesNotInvalidateCurrentBadge()throws Exception {
  auth("OPERATOR");var ops=mock(OperatorService.class);when(ops.getByFirebaseUid("uid-test")).thenReturn(operator());var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();var c=new OperatorBadgeController(ops,mock(QrCodeService.class),builder.build());ReflectionTestUtils.setField(c,"apiKey","fixture-key");server.expect(anything()).andRespond(withServerError());assertEquals(503,c.resetOwn().getStatusCode().value());verify(ops,never()).regenerateQrToken(anyString());server.verify();
 }
}
