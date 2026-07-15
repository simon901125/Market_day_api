package com.example.demo.Controller;

import static org.mockito.ArgumentMatchers.any;
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

import com.example.demo.Service.MarketEventService;
import com.example.demo.dto.request.MarketSearchRequest;
import com.example.demo.dto.response.ApiResponse;

@ExtendWith(MockitoExtension.class)
class MarketControllerTest {
    @Mock MarketEventService service;
    MockMvc mvc;

    @BeforeEach void setUp() {
        MarketController controller = new MarketController();
        ReflectionTestUtils.setField(controller, "marketEventService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test void searchAndDetailDelegateToService() throws Exception {
        when(service.searchMarkets(any())).thenReturn(ApiResponse.success("ok", java.util.List.of()));
        mvc.perform(post("/api/markets/search").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/markets/11")).andExpect(status().isOk());
        verify(service).searchMarkets(any(MarketSearchRequest.class));
        verify(service).getMarketDetail(11L);
    }
}
