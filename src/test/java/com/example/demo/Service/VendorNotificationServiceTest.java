package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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
import com.example.demo.enums.notification.NotificationCategory;

@ExtendWith(MockitoExtension.class)
class VendorNotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock StallRepository stallRepository;
    @Mock JwtService jwtService;

    private VendorNotificationService service;

    @BeforeEach
    void setUp() {
        service = new VendorNotificationService(notificationRepository, stallRepository, jwtService, 1);
    }

    @Test
    void applicationReviewFilterReturnsPagedCurrentVendorNotifications() {
        authenticateVendor(7L);
        VendorNotificationItemResponse item = new VendorNotificationItemResponse(
                30L, "APPLICATION_REVIEW", "APPLICATION_APPROVED",
                "EVENT_APPLICATION", 9L, "待付款", "請完成付款",
                false, null, LocalDateTime.of(2026, 7, 15, 10, 0));
        when(notificationRepository.countVendorNotifications(
                eq(7L), eq(NotificationCategory.APPLICATION_REVIEW), eq(false), any(LocalDateTime.class)))
                .thenReturn(1L);
        when(notificationRepository.countVendorNotifications(
                eq(7L), eq(null), eq(true), any(LocalDateTime.class)))
                .thenReturn(3L);
        when(notificationRepository.findVendorNotifications(
                eq(7L), eq(NotificationCategory.APPLICATION_REVIEW), eq(false),
                any(LocalDateTime.class), eq(10), eq(10)))
                .thenReturn(List.of(item));

        var response = service.getNotifications("Bearer token", "報名審核", 2, 99);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().unreadCount()).isEqualTo(3);
        assertThat(response.getData().notifications().getItems()).containsExactly(item);
        assertThat(response.getData().notifications().getPage()).isEqualTo(2);
        assertThat(response.getData().notifications().getPageSize()).isEqualTo(10);
    }

    @Test
    void unreadFilterQueriesAllCategoriesButOnlyUnreadRows() {
        authenticateVendor(7L);
        when(notificationRepository.countVendorNotifications(
                eq(7L), eq(null), eq(true), any(LocalDateTime.class)))
                .thenReturn(4L);
        when(notificationRepository.findVendorNotifications(
                eq(7L), eq(null), eq(true), any(LocalDateTime.class), eq(0), eq(10)))
                .thenReturn(List.of());

        var response = service.getNotifications("Bearer token", "未讀", 1, 10);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().notifications().getTotalItems()).isEqualTo(4);
    }

    @Test
    void newVendorWithoutProfileReturnsEmptyNotificationPage() {
        authenticateVendor(7L);
        when(notificationRepository.countVendorNotifications(
                eq(7L), eq(null), eq(false), any(LocalDateTime.class)))
                .thenReturn(0L);
        when(notificationRepository.countVendorNotifications(
                eq(7L), eq(null), eq(true), any(LocalDateTime.class)))
                .thenReturn(0L);
        when(notificationRepository.findVendorNotifications(
                eq(7L), eq(null), eq(false), any(LocalDateTime.class), eq(0), eq(10)))
                .thenReturn(List.of());

        var response = service.getNotifications("Bearer token", "全部", 1, 10);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().unreadCount()).isZero();
        assertThat(response.getData().notifications().getTotalItems()).isZero();
        assertThat(response.getData().notifications().getItems()).isEmpty();
    }

    @Test
    void invalidFilterAndNonVendorAreRejectedBeforeNotificationQuery() {
        authenticateVendor(7L);
        var invalidFilter = service.getNotifications("Bearer token", "SYSTEM", 1, 10);

        assertThat(invalidFilter.isSuccessStatus()).isFalse();
        verify(notificationRepository, never()).findVendorNotifications(
                any(), any(), anyBoolean(), any(), anyInt(), anyInt());

        when(jwtService.getRole("other-token")).thenReturn("ORGANIZER");
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer other-token")).thenReturn("other-token");
        when(jwtService.isTokenValid("other-token")).thenReturn(true);
        var wrongRole = service.getNotifications("Bearer other-token", "全部", 1, 10);

        assertThat(wrongRole.isSuccessStatus()).isFalse();
    }

    private void authenticateVendor(Long userId) {
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.getRole("token")).thenReturn("VENDOR");
        when(jwtService.getEmail("token")).thenReturn("vendor@example.test");
        when(stallRepository.findVendorDashboardProfileByEmail("vendor@example.test"))
                .thenReturn(Optional.of(Map.of("userId", userId, "role", "VENDOR")));
    }
}
