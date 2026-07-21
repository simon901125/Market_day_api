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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.demo.Service.MarketEventService;
import com.example.demo.dto.request.MarketSearchRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.PageResponse;

@ExtendWith(MockitoExtension.class)
class MarketControllerTest {
    @Mock MarketEventService service;
    MockMvc mvc;

    @BeforeEach void setUp() {
        AllController controller = new AllController();
        ReflectionTestUtils.setField(controller, "marketEventService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test void searchAndDetailDelegateToService() throws Exception {
        when(service.searchMarkets(any(), eq(2), eq(5)))
                .thenReturn(ApiResponse.success("ok", PageResponse.from(java.util.List.of(), 2, 5)));
        mvc.perform(post("/api/markets/search")
                        .param("eventType", "目前活動")
                        .param("keyword", "市集")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-31")
                        .param("city", "臺北市")
                        .param("eventStatus", "即將開始")
                        .param("categoryNames", "餐飲美食,文創手作")
                        .param("page", "2")
                        .param("pageSize", "5"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/markets/11")).andExpect(status().isOk());
        verify(service).searchMarkets(any(MarketSearchRequest.class), eq(2), eq(5));
        verify(service).getMarketDetail(11L, null, null);
    }
}
