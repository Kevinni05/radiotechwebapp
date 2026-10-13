package com.radiotech.radiotech_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AuthChangePasswordTest {
    private static final String SIGN_IN = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fixture-key";
    private static final String UPDATE = "https://identitytoolkit.googleapis.com/v1/accounts:update?key=fixture-key";
    private AuthService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new AuthService(mock(OperatorService.class), mock(CapoService.class), builder.build());
        ReflectionTestUtils.setField(service, "firebaseWebApiKey", "fixture-key");
    }

    @Test
    void changesOnlyAuthenticatedUserWithoutEmailAndPreservesPasswordWhitespace() {
        server.expect(requestTo(SIGN_IN)).andExpect(method(HttpMethod.POST))
            .andExpect(content().json("{\"email\":\"capo@example.test\",\"password\":\" OldPass1! \",\"returnSecureToken\":true}"))
            .andRespond(withSuccess("{\"localId\":\"chief-uid\",\"idToken\":\"fresh-user-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(UPDATE)).andExpect(method(HttpMethod.POST))
            .andExpect(content().json("{\"idToken\":\"fresh-user-token\",\"password\":\" NewPass2! \",\"returnSecureToken\":true}"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        service.changePassword("chief-uid", "capo@example.test", " OldPass1! ", " NewPass2! ");
        server.verify();
    }

    @Test
    void wrongCurrentPasswordDoesNotUpdateAccountOrExposeProviderResponse() {
        server.expect(requestTo(SIGN_IN)).andRespond(withBadRequest()
            .body("{\"error\":{\"message\":\"INVALID_LOGIN_CREDENTIALS secret-fixture\"}}"));
        var error = assertThrows(IllegalArgumentException.class,
            () -> service.changePassword("chief-uid", "capo@example.test", "wrong", "NewPass2!"));
        assertTrue(error.getMessage().contains("Password attuale"));
        assertFalse(error.getMessage().contains("secret-fixture"));
        server.verify();
    }

    @Test
    void refusesCredentialsForAnotherUser() {
        server.expect(requestTo(SIGN_IN)).andRespond(withSuccess(
            "{\"localId\":\"other-uid\",\"idToken\":\"other-user-token\"}", MediaType.APPLICATION_JSON));
        assertThrows(SecurityException.class,
            () -> service.changePassword("chief-uid", "capo@example.test", "OldPass1!", "NewPass2!"));
        server.verify();
    }

    @Test
    void rejectsWeakIdenticalAndMissingPasswordsBeforeCallingProvider() {
        for (String password : new String[]{"short", "lowercase1!", "UPPERCASE1!", "NoDigits!!", "NoSymbol12", "OldPass1!"}) {
            assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("chief-uid", "capo@example.test", "OldPass1!", password));
        }
        assertThrows(IllegalArgumentException.class,
            () -> service.changePassword("chief-uid", "capo@example.test", null, "NewPass2!"));
        assertThrows(SecurityException.class,
            () -> service.changePassword(null, "capo@example.test", "OldPass1!", "NewPass2!"));
        server.verify();
    }

    @Test
    void doesNotBypassSecondFactor() {
        server.expect(requestTo(SIGN_IN)).andRespond(withSuccess(
            "{\"mfaPendingCredential\":\"pending-mfa\"}", MediaType.APPLICATION_JSON));
        var error = assertThrows(IllegalArgumentException.class,
            () -> service.changePassword("chief-uid", "capo@example.test", "OldPass1!", "NewPass2!"));
        assertTrue(error.getMessage().contains("secondo fattore"));
        server.verify();
    }

    @Test
    void reportsProviderOutageWithoutAttemptingUpdate() {
        server.expect(requestTo(SIGN_IN)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThrows(IllegalStateException.class,
            () -> service.changePassword("chief-uid", "capo@example.test", "OldPass1!", "NewPass2!"));
        server.verify();
    }

    @Test
    void providerPolicyRejectionDoesNotClaimSuccess() {
        server.expect(requestTo(SIGN_IN)).andRespond(withSuccess(
            "{\"localId\":\"chief-uid\",\"idToken\":\"fresh-user-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(UPDATE)).andRespond(withBadRequest()
            .body("{\"error\":{\"message\":\"PASSWORD_DOES_NOT_MEET_REQUIREMENTS\"}}"));
        assertThrows(IllegalArgumentException.class,
            () -> service.changePassword("chief-uid", "capo@example.test", "OldPass1!", "NewPass2!"));
        server.verify();
    }
}
