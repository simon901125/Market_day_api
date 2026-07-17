package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.Repository.NotificationRepository;
import com.example.demo.Repository.StallRepository;
import com.example.demo.dto.response.VendorNotificationItemResponse;

@ExtendWith(MockitoExtension.class)
class VendorDashboardServiceTest {

    @Mock StallRepository stallRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock JwtService jwtService;

    private VendorDashboardService service;

    @BeforeEach
    void setUp() {
        service = new VendorDashboardService(stallRepository, notificationRepository, jwtService, 1);
    }

    @Test
    void incompleteProfileReturnsFirstLoginGuideWithoutDashboardQueries() {
        authenticateVendor();
        Map<String, Object> incomplete = completeVendor();
        incomplete.put("coverImageUrl", null);
        when(stallRepository.findVendorDashboardProfileByEmail("vendor@example.test"))
                .thenReturn(Optional.of(incomplete));
        var response = service.initDashboard("Bearer token");

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().needsProfile()).isTrue();
        assertThat(response.getData().guideMessage()).isEqualTo("請先完成攤位必填資料");
        assertThat(response.getData().notifications()).isEmpty();
        verify(stallRepository, never()).findVendorDashboardApplicationCounts(any());
        verify(notificationRepository, never()).findVendorNotifications(
                any(), any(), eq(false), any(), eq(0), eq(6));
    }

    @Test
    void completeProfileReturnsCountsAndLatestNotifications() {
        authenticateVendor();
        when(stallRepository.findVendorDashboardProfileByEmail("vendor@example.test"))
                .thenReturn(Optional.of(completeVendor()));
        when(stallRepository.findVendorCategoriesByProfileIds(List.of(20L)))
                .thenReturn(List.of(Map.of(
                        "vendorProfileId", 20L, "id", 1L, "name", "Food", "slug", "food")));
        when(stallRepository.findVendorDashboardApplicationCounts(7L)).thenReturn(Map.of(
                "pendingReviewCount", 12,
                "pendingPaymentCount", 6,
                "pendingStallSelectionCount", 2));
        VendorNotificationItemResponse notification = new VendorNotificationItemResponse(
                30L,
                "APPLICATION_REVIEW",
                "APPLICATION_SUBMITTED",
                "EVENT_APPLICATION",
                9L,
                "待審核",
                "報名已送出",
                false,
                null,
                LocalDateTime.of(2026, 7, 15, 10, 0));
        when(notificationRepository.findVendorNotifications(
                eq(7L), eq(null), eq(false), any(LocalDateTime.class), eq(0), eq(6)))
                .thenReturn(List.of(notification));

        var response = service.initDashboard("Bearer token");

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().needsProfile()).isFalse();
        assertThat(response.getData().name()).isEqualTo("王小明");
        assertThat(response.getData().pendingReviewCount()).isEqualTo(12);
        assertThat(response.getData().pendingPaymentCount()).isEqualTo(6);
        assertThat(response.getData().pendingStallSelectionCount()).isEqualTo(2);
        assertThat(response.getData().notifications()).containsExactly(notification);
    }

    @Test
    void emptyProductsDoNotKeepProfileInFirstLoginState() {
        authenticateVendor();
        when(stallRepository.findVendorDashboardProfileByEmail("vendor@example.test"))
                .thenReturn(Optional.of(completeVendor()));
        when(stallRepository.findVendorCategoriesByProfileIds(List.of(20L)))
                .thenReturn(List.of(Map.of(
                        "vendorProfileId", 20L, "id", 1L, "name", "Food", "slug", "food")));
        when(stallRepository.findVendorDashboardApplicationCounts(7L)).thenReturn(Map.of(
                "pendingReviewCount", 0,
                "pendingPaymentCount", 0,
                "pendingStallSelectionCount", 0));
        when(notificationRepository.findVendorNotifications(
                eq(7L), eq(null), eq(false), any(LocalDateTime.class), eq(0), eq(6)))
                .thenReturn(List.of());

        var response = service.initDashboard("Bearer token");

        assertThat(response.getData().needsProfile()).isFalse();
        verify(stallRepository, never()).findVendorProducts(any());
    }

    private void authenticateVendor() {
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.getRole("token")).thenReturn("VENDOR");
        when(jwtService.getEmail("token")).thenReturn("vendor@example.test");
    }

    private Map<String, Object> completeVendor() {
        Map<String, Object> vendor = new LinkedHashMap<>();
        vendor.put("userId", 7L);
        vendor.put("role", "VENDOR");
        vendor.put("userProfileId", 10L);
        vendor.put("vendorProfileId", 20L);
        vendor.put("name", "測試品牌");
        vendor.put("contactName", "王小明");
        vendor.put("contactPhone", "0912345678");
        vendor.put("contactEmail", "vendor@example.test");
        vendor.put("city", "臺北市");
        vendor.put("district", "中正區");
        vendor.put("address", "測試路 1 號");
        vendor.put("categoryId", 3L);
        vendor.put("avatarImageUrl", "/images/vendor/avatar.jpg");
        vendor.put("coverImageUrl", "/images/vendor/cover.jpg");
        vendor.put("brandSummary", "品牌摘要");
        vendor.put("brandDescription", "品牌介紹");
        return vendor;
    }

}
