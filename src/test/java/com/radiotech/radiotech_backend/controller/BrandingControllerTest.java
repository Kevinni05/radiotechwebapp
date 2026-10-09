package com.radiotech.radiotech_backend.controller;
import org.junit.jupiter.api.Test;
import com.radiotech.radiotech_backend.security.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BrandingControllerTest {
 @Test void onlyBoundedPresentationDataIsAccepted() {
  var result=BrandingController.validate(Map.of("name","Azienda <script>","colors",Map.of("accent","#128abc","lightSurface","#ffffff"),"labels",Map.of("nav-antennas","Impianti"),"tenantId","other"));
  assertEquals("Azienda <script>",result.get("name"));assertFalse(result.containsKey("tenantId"));
  assertThrows(IllegalArgumentException.class,()->BrandingController.validate(Map.of("colors",Map.of("accent","red;display:none"))));
  assertThrows(IllegalArgumentException.class,()->BrandingController.validate(Map.of("logo","data:image/svg+xml;base64,abc")));
  assertThrows(IllegalArgumentException.class,()->BrandingController.validate(Map.of("labels",Map.of("x","x".repeat(501)))));
 }
 @Test void viewerCannotWriteEvenInOwnTenant() throws Exception {
  var auth=new UsernamePasswordAuthenticationToken("viewer",null,List.of(new SimpleGrantedAuthority("ROLE_VIEWER")));var details=new FirebaseAuthenticationDetails("viewer","a@b.test","VIEWER","tenant-a");auth.setDetails(details);SecurityContextHolder.getContext().setAuthentication(auth);
  try {assertThrows(SecurityException.class,()->new BrandingController().save(Map.of()));}finally{SecurityContextHolder.clearContext();}
 }
}
