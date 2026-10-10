package com.radiotech.radiotech_backend.service;
import com.google.firebase.auth.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AuthEnterpriseRoleTest {
 @Test void enterpriseRolesUseVerifiedClaimsWithoutRequiringAnOperatorProfile()throws Exception{
  for(String role:List.of("ADMIN","SUPER_ADMIN","NETWORK_MANAGER","ENGINEER","VIEWER")){
   var firebase=mock(FirebaseAuth.class);var token=mock(FirebaseToken.class);when(token.getUid()).thenReturn("user");when(token.getClaims()).thenReturn(Map.of("role",role,"tenantId","tenant"));when(firebase.verifyIdToken("id-token", true)).thenReturn(token);
   var operators=mock(OperatorService.class);
   try(var auth=mockStatic(FirebaseAuth.class)){auth.when(FirebaseAuth::getInstance).thenReturn(firebase);var response=new AuthService(operators,mock(CapoService.class),mock(RestClient.class)).loginWithFirebaseToken("id-token");assertEquals(role,response.get("role"));assertEquals(false,response.get("operatorFound"));verifyNoInteractions(operators);}
  }
 }
}
