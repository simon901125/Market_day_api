package com.example.demo.Filter;

import java.io.IOException;
import java.util.Map;
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

    private final JwtService jwtService;
    private final UpdateActiveTimeService updateActiveTimeService;
    private final ObjectMapper objectMapper;

    //api放置處
    private final Set<ProtectedApi> protectedApis = Set.of(
            new ProtectedApi(HttpMethod.POST.name(), "/api/auth/logout"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/auth/google-bind"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/auth/me"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/account/deactivate"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/images"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/vendor/account"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/vendor/applications/search"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/vendor/applications/{id}"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/vendor/stall/load"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/vendor/stall/save"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/vendor/stall-map/{applicationNo}"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/vendor/payments/newebpay"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/vendor/payments/{applicationNo}/status"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/vendor/payments/{applicationNo}/newebpay-query"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/stalls/select"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/organizer/dashboard/init"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/organizer/applications/search"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/organizer/applications/{id}"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/organizer/accounts/{eventId}"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/organizer/accounts/{eventId}/export"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/organizer/stalls/search"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/organizer/equipment/search"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/organizer/equipment/{eventId}"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/organizer/equipment/{eventId}/export"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/organizer/stall/{eventId}"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/organizer/stall/{eventId}/{stallNo}"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/organizer/applications/{id}/approve"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/organizer/applications/{id}/reject"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/admin/dashboard/overview"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/admin/notices/search"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/admin/events/search"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/admin/events/{id}"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/admin/events/{id}/approve"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/admin/events/{id}/request-revision"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/admin/events/{id}/map-complete"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/admin/events/{id}/unpublish-confirm"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/admin/users/search"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/admin/users/{id}/vender"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/admin/users/{id}/organizer"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/admin/users/{id}/venderReg"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/admin/users/{id}/OrgEvent"),
            new ProtectedApi(HttpMethod.GET.name(), "/api/admin/users/{id}/loginLog"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/admin/users/{id}/disable"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/admin/users/{id}/restore"),
            new ProtectedApi(HttpMethod.POST.name(), "/api/admin/logs/search"));

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

        //非目標apis直接放行
        if (!isProtectedApi(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        //目標api，做驗證
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

        filterChain.doFilter(request, response);
    }
    //將目前的請求包裝成:ProtectedApi("GET", "/api/auth/me")，來做後續比對
    private boolean isProtectedApi(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return protectedApis.stream()
                .anyMatch(api -> api.method().equals(method) && matchesPath(api.path(), path));
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
    //編寫錯誤回報
    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(HttpServletResponse.SC_UNAUTHORIZED, message));
    }

    private record ProtectedApi(String method, String path) {
    }
}

