package com.example.demo.Controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.demo.Service.StallService;
import com.example.demo.dto.request.StallSelectionRequest;
import com.example.demo.dto.request.VendorStallSaveRequest;
import com.example.demo.dto.response.ApiResponse;

@ExtendWith(MockitoExtension.class)
class StallControllerTest {
    @Mock StallService service;
    MockMvc mvc;
    static final String AUTH = "Bearer token";

    @BeforeEach void setUp() {
        StallController controller = new StallController();
        ReflectionTestUtils.setField(controller, "stallService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test void selectionEndpointDelegatesValidRequest() throws Exception {
        when(service.selectEventStall(eq(AUTH), any())).thenReturn(ApiResponse.success("ok", null));
        mvc.perform(post("/api/stalls/select").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicationNo\":\"APP-1\",\"selections\":[{\"applyDate\":\"2026-08-02\",\"stallNo\":\"A01\"}]}"))
                .andExpect(status().isOk());
        verify(service).selectEventStall(eq(AUTH), any(StallSelectionRequest.class));
    }

    @Test void vendorProfileEndpointsDelegate() throws Exception {
        mvc.perform(get("/api/vendor/account").header("Authorization", AUTH)).andExpect(status().isOk());
        mvc.perform(get("/api/vendor/stall/load").header("Authorization", AUTH)).andExpect(status().isOk());
        mvc.perform(post("/api/vendor/stall/save").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        verify(service).getVendorAccount(AUTH);
        verify(service).loadVendorStallProfile(AUTH);
        verify(service).saveVendorStallProfile(eq(AUTH), any(VendorStallSaveRequest.class));
    }

    @Test void vendorStallMapParsesDate() throws Exception {
        mvc.perform(get("/api/vendor/stall-map/APP-1").header("Authorization", AUTH)
                .param("applyDate", "2026-08-02")).andExpect(status().isOk());
        verify(service).getVendorStallMap(AUTH, "APP-1", java.time.LocalDate.of(2026, 8, 2));
    }

    @Test void vendorApplicationSearchParsesFiltersAndPagination() throws Exception {
        mvc.perform(get("/api/vendor/applications/search")
                .header("Authorization", AUTH)
                .param("eventTitle", "夏日")
                .param("status", "待審核")
                .param("event_start_at", "2026-08-01")
                .param("event_end_at", "2026-08-31")
                .param("page", "2")
                .param("pageSize", "10"))
                .andExpect(status().isOk());

        verify(service).searchVendorApplications(
                AUTH,
                "夏日",
                "待審核",
                java.time.LocalDate.of(2026, 8, 1),
                java.time.LocalDate.of(2026, 8, 31),
                2,
                10);
    }

    @Test void vendorApplicationSearchUsesScreenPaginationDefaults() throws Exception {
        mvc.perform(get("/api/vendor/applications/search")
                .header("Authorization", AUTH))
                .andExpect(status().isOk());

        verify(service).searchVendorApplications(
                AUTH,
                null,
                null,
                null,
                null,
                1,
                10);
    }

    @Test void vendorApplicationDetailDelegatesApplicationId() throws Exception {
        mvc.perform(get("/api/vendor/applications/8")
                .header("Authorization", AUTH))
                .andExpect(status().isOk());

        verify(service).getVendorApplicationDetail(AUTH, 8L);
    }
}
