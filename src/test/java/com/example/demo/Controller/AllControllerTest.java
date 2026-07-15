package com.example.demo.Controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.demo.Service.BrandService;
import com.example.demo.Service.ImageStorageService;
import com.example.demo.Service.StallService;
import com.example.demo.Service.TaiwanAddressService;
import com.example.demo.dto.request.BrandSearchRequest;
import com.example.demo.dto.response.ApiResponse;

@ExtendWith(MockitoExtension.class)
class AllControllerTest {
    @Mock StallService stallService;
    @Mock BrandService brandService;
    @Mock ImageStorageService imageStorageService;
    @Mock TaiwanAddressService addressService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        AllController controller = new AllController();
        ReflectionTestUtils.setField(controller, "stallService", stallService);
        ReflectionTestUtils.setField(controller, "brandService", brandService);
        ReflectionTestUtils.setField(controller, "imageStorageService", imageStorageService);
        ReflectionTestUtils.setField(controller, "taiwanAddressService", addressService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void addressEndpointsValidateCity() throws Exception {
        when(addressService.isValidCity("Taipei")).thenReturn(true);
        mvc.perform(get("/api/addresses/cities")).andExpect(status().isOk());
        mvc.perform(get("/api/addresses/districts").param("city", "Taipei")).andExpect(status().isOk());
        mvc.perform(get("/api/addresses/districts").param("city", "invalid")).andExpect(status().isOk());
        verify(addressService).cities();
        verify(addressService).districts("Taipei");
    }

    @Test
    void imageEndpointForwardsAllUploadMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1});
        mvc.perform(multipart("/api/images").file(file)
                        .header("Authorization", "Bearer token")
                        .param("purpose", "PRODUCT")
                        .param("productId", "7"))
                .andExpect(status().isOk());
        verify(imageStorageService).store(eq("Bearer token"), eq("PRODUCT"), eq(7L), eq(null), any());
    }

    @Test
    void publicStallStatusForwardsOptionalDate() throws Exception {
        when(stallService.getPublicEventStallsStatus(eq(3L), any())).thenReturn(ApiResponse.success("ok", java.util.List.of()));
        mvc.perform(get("/api/eventsMap/3/stallsStatus").param("applyDate", "2026-08-01"))
                .andExpect(status().isOk());
        verify(stallService).getPublicEventStallsStatus(3L, java.time.LocalDate.of(2026, 8, 1));
    }

    @Test
    void brandEndpointsDelegateSearchOptionsAndDetail() throws Exception {
        mvc.perform(get("/api/brands/scroll-options")).andExpect(status().isOk());
        mvc.perform(get("/api/brands/search").param("keyword", "tea").param("page", "2").param("pageSize", "8"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/brands/9")).andExpect(status().isOk());
        verify(brandService).getBrandScrollOptions();
        verify(brandService).searchBrands(any(BrandSearchRequest.class));
        verify(brandService).getBrandDetail(9L);
    }
}
