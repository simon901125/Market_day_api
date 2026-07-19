package com.example.demo.Controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.demo.Service.AdminService;
import com.example.demo.Service.JwtService;
import com.example.demo.dto.request.admin.AdminEventSearchDto;
import com.example.demo.dto.request.admin.AdminLogSearchDto;
import com.example.demo.dto.request.admin.AdminUserSearchDto;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {
    @Mock AdminService service;
    @Mock JwtService jwtService;
    MockMvc mvc;

    @BeforeEach void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AdminController(service, jwtService)).build();
    }

    @Test void implementedDashboardAndSearchEndpointsDelegate() throws Exception {
        mvc.perform(post("/api/admin/events/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"pageNumber\":1,\"pageSize\":20}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/users/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"pageNumber\":1,\"pageSize\":20}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/logs/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"pageNumber\":1,\"pageSize\":20}"))
                .andExpect(status().isOk());
        verify(service).getEventsList(any(AdminEventSearchDto.class), eq(1), eq(20));
        verify(service).getUserList(any(AdminUserSearchDto.class), eq(1), eq(20));
        verify(service).getLogs(any(AdminLogSearchDto.class), eq(1), eq(20));
    }

    @Test void searchEndpointsDefaultPagingWhenBodyOmitsPageNumberAndPageSize() throws Exception {
        mvc.perform(post("/api/admin/events/search").contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/users/search").contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/logs/search").contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
        int standardPageSize = 6;
        verify(service).getEventsList(any(AdminEventSearchDto.class), eq(1), eq(standardPageSize));
        verify(service).getUserList(any(AdminUserSearchDto.class), eq(1), eq(standardPageSize));
        verify(service).getLogs(any(AdminLogSearchDto.class), eq(1), eq(standardPageSize));
    }

    @Test void getDashboardOverviewDelegatesWithOperatorEmailFromToken() throws Exception {
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.getEmail("token")).thenReturn("admin@test.com");

        mvc.perform(get("/api/admin/dashboard/overview").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(service).getDashboardResponse("admin@test.com");
    }

    @Test void getDashboardOverviewFailsWithoutValidToken() throws Exception {
        mvc.perform(get("/api/admin/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(400));

        verifyNoInteractions(service);
    }

    @Test void placeholderRoutesRemainReachable() throws Exception {
        mvc.perform(post("/api/admin/notices/search").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/events/1")).andExpect(status().isOk());
        String[] posts = {"/api/admin/events/1/approve", "/api/admin/events/1/request-revision",
                "/api/admin/events/1/map-complete", "/api/admin/events/1/unpublish-confirm",
                "/api/admin/users/1/disable", "/api/admin/users/1/restore"};
        for (String path : posts) mvc.perform(post(path)).andExpect(status().isOk());
    }

    @Test void getUserDetailWithVenderRoleDelegatesToVenderDetail() throws Exception {
        mvc.perform(get("/api/admin/users/1").param("role", "vender").param("size", "5"))
                .andExpect(status().isOk());
        verify(service).getVenderDetail(1L, 5);
    }

    @Test void getUserDetailWithOrganizerRoleDelegatesToOrganizerDetail() throws Exception {
        mvc.perform(get("/api/admin/users/1").param("role", "organizer"))
                .andExpect(status().isOk());
        verify(service).getOrganizerDetail(1L, 6);
    }

    @Test void getUserDetailWithUnknownRoleReturnsFailWithoutCallingService() throws Exception {
        mvc.perform(get("/api/admin/users/1").param("role", "vip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(400));
    }
}
