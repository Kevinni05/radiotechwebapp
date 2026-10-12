package com.radiotech.radiotech_backend.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StreamingSecurityTest {
    @Configuration @EnableWebSecurity @EnableWebMvc @Import(SecurityConfig.class)
    static class Fixture {
        @Bean FirebaseAuthenticationFilter firebaseAuthenticationFilter() throws Exception {
            var filter=mock(FirebaseAuthenticationFilter.class);
            doAnswer(call->{
                var request=(HttpServletRequest)call.getArgument(0);
                if(request.getDispatcherType()==DispatcherType.REQUEST&&"Bearer fixture".equals(request.getHeader("Authorization")))
                    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("fixture",null,"ROLE_ADMIN"));
                ((FilterChain)call.getArgument(2)).doFilter(call.getArgument(0),call.getArgument(1));
                return null;
            }).when(filter).doFilter(any(),any(),any());
            return filter;
        }
        @Bean StreamEndpoint streamEndpoint(){return new StreamEndpoint();}
    }
    @RestController static class StreamEndpoint {
        @GetMapping("/api/private-stream") StreamingResponseBody stream(){return output->output.write("verified bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }
    @Test void authorizedStreamCompletesAsyncWhileAnonymousInitialRequestIsDenied()throws Exception {
        try(var context=new AnnotationConfigWebApplicationContext()){
            context.setServletContext(new MockServletContext());context.register(Fixture.class);context.refresh();
            var mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
            mvc.perform(get("/api/private-stream")).andExpect(status().isForbidden());
            var started=mvc.perform(get("/api/private-stream").header("Authorization","Bearer fixture"))
                .andExpect(request().asyncStarted()).andReturn();
            SecurityContextHolder.clearContext();
            mvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andExpect(content().string("verified bytes"));
        }finally{SecurityContextHolder.clearContext();}
    }
}
