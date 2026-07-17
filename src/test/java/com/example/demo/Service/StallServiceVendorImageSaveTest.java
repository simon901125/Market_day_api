package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Repository.StallRepository;
import com.example.demo.dto.request.VendorStallSaveRequest;
import com.example.demo.dto.request.VendorProductSaveRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.MapBackedResponse;

class StallServiceVendorImageSaveTest {

    private static final String AUTHORIZATION = "Bearer valid-token";

    private StallRepository stallRepository;
    private JwtService jwtService;
    private TaiwanAddressService taiwanAddressService;
    private StallService stallService;

    @BeforeEach
    void setUp() {
        stallRepository = org.mockito.Mockito.mock(StallRepository.class);
        jwtService = org.mockito.Mockito.mock(JwtService.class);
        taiwanAddressService = org.mockito.Mockito.mock(TaiwanAddressService.class);
        stallService = new StallService();
        ReflectionTestUtils.setField(stallService, "stallRepository", stallRepository);
        ReflectionTestUtils.setField(stallService, "jwtService", jwtService);
        ReflectionTestUtils.setField(stallService, "taiwanAddressService", taiwanAddressService);

        when(jwtService.extractTokenFromAuthorizationHeader(AUTHORIZATION)).thenReturn("valid-token");
        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.getRole("valid-token")).thenReturn("VENDOR");
        when(jwtService.getEmail("valid-token")).thenReturn("vendor1@example.test");
        when(stallRepository.findVendorAccountByEmail("vendor1@example.test"))
                .thenReturn(Optional.of(vendorData()));
        when(stallRepository.findActiveCategoriesByIds(List.of(7L)))
                .thenReturn(List.of(Map.of("id", 7L, "name", "餐飲美食", "slug", "food")));
        when(stallRepository.updateVendorProfile(anyLong(), anyLong(), anyMap())).thenReturn(1);
        when(stallRepository.replaceVendorProducts(20L, List.of())).thenReturn(0);
        when(stallRepository.findVendorProducts(20L)).thenReturn(List.of());
        when(taiwanAddressService.isValidCity("台北市")).thenReturn(true);
        when(taiwanAddressService.isValidDistrict("台北市", "信義區")).thenReturn(true);
    }

    @Test
    void profileSaveDoesNotAttemptToOverwriteImageUrls() {
        ApiResponse<MapBackedResponse> response = stallService.saveVendorStallProfile(
                AUTHORIZATION,
                validRequest());

        assertThat(response.isSuccessStatus()).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> profileCaptor = ArgumentCaptor.forClass(Map.class);
        verify(stallRepository).updateVendorProfile(eq(10L), eq(20L), profileCaptor.capture());
        verify(stallRepository).replaceVendorProducts(20L, List.of());
        assertThat(profileCaptor.getValue()).doesNotContainKeys("avatarImageUrl", "coverImageUrl");
    }

    @Test
    void existingProductIdIsPassedThroughForInPlaceUpdate() {
        VendorStallSaveRequest request = validRequest();
        VendorProductSaveRequest product = new VendorProductSaveRequest();
        product.setId(88L);
        product.setProductName("既有商品");
        product.setProductSummary("保留原本 ID");
        product.setProductPrice(new BigDecimal("120"));
        product.setProductImageUrl("http://localhost:8081/images/product/existing.png");
        request.setProducts(List.of(product));
        when(stallRepository.replaceVendorProducts(eq(20L), anyList())).thenReturn(1);

        ApiResponse<MapBackedResponse> response = stallService.saveVendorStallProfile(
                AUTHORIZATION,
                request);

        assertThat(response.isSuccessStatus()).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> productsCaptor = ArgumentCaptor.forClass(List.class);
        verify(stallRepository).replaceVendorProducts(eq(20L), productsCaptor.capture());
        assertThat(productsCaptor.getValue()).singleElement()
                .satisfies(savedProduct -> assertThat(savedProduct.get("id")).isEqualTo(88L));
    }

    private VendorStallSaveRequest validRequest() {
        VendorStallSaveRequest request = new VendorStallSaveRequest();
        request.setBrandName("測試攤位");
        request.setContactName("王小明");
        request.setContactPhone("0912345678");
        request.setContactEmail("vendor1@example.test");
        request.setCity("台北市");
        request.setDistrict("信義區");
        request.setAddress("市府路 1 號");
        request.setBrandSummary("測試摘要");
        request.setBrandDescription("測試介紹");
        request.setCategoryIds(List.of(7L));
        request.setProducts(List.of());
        return request;
    }

    private Map<String, Object> vendorData() {
        return Map.ofEntries(
                Map.entry("userId", 10L),
                Map.entry("vendorProfileId", 20L),
                Map.entry("email", "vendor1@example.test"),
                Map.entry("role", "VENDOR"),
                Map.entry("name", "測試攤位"),
                Map.entry("contactName", "王小明"),
                Map.entry("contactPhone", "0912345678"),
                Map.entry("contactEmail", "vendor1@example.test"),
                Map.entry("city", "台北市"),
                Map.entry("district", "信義區"),
                Map.entry("address", "市府路 1 號"),
                Map.entry("avatarImageUrl", "http://localhost:8081/images/vendor-avatar/existing.png"),
                Map.entry("coverImageUrl", "http://localhost:8081/images/vendor-cover/existing.png"),
                Map.entry("brandSummary", "測試摘要"),
                Map.entry("brandDescription", "測試介紹"),
                Map.entry("brandType", "餐飲美食"));
    }
}
