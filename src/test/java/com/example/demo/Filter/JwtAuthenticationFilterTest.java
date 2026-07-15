package com.example.demo.Filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.demo.Service.JwtService;
import com.example.demo.Service.UpdateActiveTimeService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private UpdateActiveTimeService updateActiveTimeService;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, updateActiveTimeService, new ObjectMapper());
    }

    @Test
    void allowsPublicApiWithoutToken() throws Exception {
        MockFilterChain chain = execute("GET", "/api/markets/1", null);

        assertThat(chain.getRequest()).isNotNull();
        verify(jwtService, never()).isTokenValid("token");
    }

    @Test
    void rejectsProtectedApiWithoutToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        when(jwtService.extractTokenFromAuthorizationHeader(null)).thenReturn(null);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("statusCode").contains("401");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void rejectsInvalidOrExpiredDatabaseSession() throws Exception {
        MockHttpServletRequest request = protectedRequest();
        MockHttpServletResponse invalidResponse = new MockHttpServletResponse();
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(false, true);

        filter.doFilter(request, invalidResponse, new MockFilterChain());
        assertThat(invalidResponse.getStatus()).isEqualTo(401);

        MockHttpServletResponse expiredSessionResponse = new MockHttpServletResponse();
        when(updateActiveTimeService.isCurrentLoginSession("token")).thenReturn(false);
        filter.doFilter(protectedRequest(), expiredSessionResponse, new MockFilterChain());
        assertThat(expiredSessionResponse.getStatus()).isEqualTo(401);
    }

    @Test
    void allowsValidProtectedPathIncludingPathVariable() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/vendor/stall-map/MD001");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(updateActiveTimeService.isCurrentLoginSession("token")).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockFilterChain execute(String method, String path, String authorization)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    private MockHttpServletRequest protectedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer token");
        return request;
    }
}
