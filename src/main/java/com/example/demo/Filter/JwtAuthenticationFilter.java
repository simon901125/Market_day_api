package com.example.demo.Filter;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.demo.Service.JwtService;
import com.example.demo.Service.UpdateActiveTimeService;
import com.example.demo.dto.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_API_PREFIXES = Set.of(
            "/api/vendor/",
            "/api/organizer/",
            "/api/admin/",
            "/api/auth/",
            "/api/account/",
            "/api/images",
            "/api/stalls/");
    // ------------------------不過濾的端口(提供非登入使用者使用)------------------------
    private static final Set<PublicApi> PUBLIC_APIS = Set.of(
            new PublicApi(HttpMethod.POST.name(), "/api/vendor/local-register"),
            new PublicApi(HttpMethod.POST.name(), "/api/vendor/google-register"),
            new PublicApi(HttpMethod.POST.name(), "/api/vendor/local-login"),
            new PublicApi(HttpMethod.POST.name(), "/api/vendor/google-login"),
            new PublicApi(HttpMethod.POST.name(), "/api/vendor/markets/search"),
            new PublicApi(HttpMethod.GET.name(), "/api/vendor/markets/{id}"),
            new PublicApi(HttpMethod.POST.name(), "/api/organizer/local-register"),
            new PublicApi(HttpMethod.POST.name(), "/api/organizer/google-register"),
            new PublicApi(HttpMethod.POST.name(), "/api/organizer/local-login"),
            new PublicApi(HttpMethod.POST.name(), "/api/organizer/google-login"),
            new PublicApi(HttpMethod.POST.name(), "/api/admin/local-login"),
            new PublicApi(HttpMethod.POST.name(), "/api/auth/createAccount/emailVerify"),
            new PublicApi(HttpMethod.POST.name(), "/api/auth/createAccount/resend"),
            new PublicApi(HttpMethod.POST.name(), "/api/auth/resetPassword/request"),
            new PublicApi(HttpMethod.POST.name(), "/api/auth/resetPassword/emailVerify"),
            new PublicApi(HttpMethod.POST.name(), "/api/auth/resetPassword/reset"));

    private final JwtService jwtService;
    private final UpdateActiveTimeService updateActiveTimeService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UpdateActiveTimeService updateActiveTimeService,
            ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.updateActiveTimeService = updateActiveTimeService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // 非目標apis直接放行
        if (!isProtectedApi(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        // 目標api，做驗證
        String token = jwtService.extractTokenFromAuthorizationHeader(request.getHeader("Authorization"));
        if (token == null || token.isBlank()) {
            writeUnauthorizedResponse(response, "Authorization token is required");
            return;
        }

        if (!jwtService.isTokenValid(token)) {
            writeUnauthorizedResponse(response, "Invalid or expired token");
            return;
        }

        if (!updateActiveTimeService.isCurrentLoginSession(token)) {
            writeUnauthorizedResponse(response, "Session expired");
            return;
        }

        if (isAdminProtectedApi(request) && !"ADMIN".equals(jwtService.getRole(token))) {
            writeForbiddenResponse(response, "This account is not an admin");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isProtectedApi(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod()) || isPublicApi(request)) {
            return false;
        }

        String path = request.getRequestURI();
        return PROTECTED_API_PREFIXES.stream().anyMatch(prefix -> path.startsWith(prefix));
    }

    private boolean isPublicApi(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return PUBLIC_APIS.stream()
                .anyMatch(api -> api.method().equals(method) && matchesPath(api.path(), path));
    }

    private boolean isAdminProtectedApi(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/admin/") && !isPublicApi(request);
    }

    private boolean matchesPath(String pattern, String path) {
        if (pattern.equals(path)) {
            return true;
        }
        if (!pattern.contains("{")) {
            return false;
        }
        String regex = "^" + pattern.replaceAll("\\{[^/]+\\}", "[^/]+") + "$";
        return path.matches(regex);
    }

    // 編寫錯誤回報
    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(HttpServletResponse.SC_UNAUTHORIZED, message));
    }

    private void writeForbiddenResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(HttpServletResponse.SC_FORBIDDEN, message));
    }

    private record PublicApi(String method, String path) {
    }
}
