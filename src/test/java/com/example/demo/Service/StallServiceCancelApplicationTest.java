package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Repository.StallRepository;

@ExtendWith(MockitoExtension.class)
class StallServiceCancelApplicationTest {

    @Mock StallRepository stallRepository;
    @Mock JwtService jwtService;
    @Mock NotificationService notificationService;

    private StallService service;

    @BeforeEach
    void setUp() {
        service = new StallService();
        ReflectionTestUtils.setField(service, "stallRepository", stallRepository);
        ReflectionTestUtils.setField(service, "jwtService", jwtService);
        ReflectionTestUtils.setField(service, "notificationService", notificationService);

        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.getRole("token")).thenReturn("VENDOR");
        when(jwtService.getEmail("token")).thenReturn("vendor@example.test");
        when(stallRepository.findVendorAccountByEmail("vendor@example.test"))
                .thenReturn(Optional.of(Map.of("userId", 7L, "role", "VENDOR")));
    }

    @Test
    void pendingReviewApplicationCanBeCancelled() {
        when(stallRepository.findVendorApplicationForCancellation(10L, 7L)).thenReturn(Optional.of(Map.of(
                "reviewStatus", "PENDING", "paymentStatus", "PENDING", "isCancelled", false,
                "organizerUserId", 20L, "eventTitle", "測試市集", "brandName", "測試品牌")));
        when(stallRepository.cancelVendorApplication(10L, 7L)).thenReturn(1);

        var response = service.cancelVendorApplication("Bearer token", 10L);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getMessage()).isEqualTo("報名取消成功");
        verify(stallRepository).cancelVendorApplication(10L, 7L);
        verify(notificationService).notifyApplicationCancelled(
                7L, 20L, 10L, "測試市集", "測試品牌");
    }

    @Test
    void pendingOrFailedPaymentApplicationCanBeCancelled() {
        when(stallRepository.findVendorApplicationForCancellation(11L, 7L)).thenReturn(Optional.of(Map.of(
                "reviewStatus", "APPROVED", "paymentStatus", "FAILED", "isCancelled", false,
                "organizerUserId", 20L, "eventTitle", "測試市集", "brandName", "測試品牌")));
        when(stallRepository.cancelVendorApplication(11L, 7L)).thenReturn(1);

        assertThat(service.cancelVendorApplication("Bearer token", 11L).isSuccessStatus()).isTrue();
    }

    @Test
    void paidApplicationCannotBeCancelled() {
        when(stallRepository.findVendorApplicationForCancellation(12L, 7L)).thenReturn(Optional.of(Map.of(
                "reviewStatus", "APPROVED", "paymentStatus", "PAID", "isCancelled", false)));

        var response = service.cancelVendorApplication("Bearer token", 12L);

        assertThat(response.isSuccessStatus()).isFalse();
        assertThat(response.getStatusCode()).isEqualTo(409);
        assertThat(response.getMessage())
                .contains("目前狀態不可取消")
                .contains("APPROVED")
                .contains("PAID");
        verify(stallRepository, never()).cancelVendorApplication(12L, 7L);
    }

    @Test
    void missingOrOwnedByAnotherVendorReturnsClearMessage() {
        when(stallRepository.findVendorApplicationForCancellation(99L, 7L)).thenReturn(Optional.empty());

        var response = service.cancelVendorApplication("Bearer token", 99L);

        assertThat(response.getMessage()).isEqualTo("找不到報名 ID 99，或該報名不屬於目前攤主");
    }
}
