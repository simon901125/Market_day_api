package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.Repository.NotificationRepository;
import com.example.demo.Repository.OrganizerRepository;
import com.example.demo.dto.response.OrganizerNotificationItemResponse;
import com.example.demo.enums.notification.NotificationCategory;

@ExtendWith(MockitoExtension.class)
class OrganizerNotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock OrganizerRepository organizerRepository;
    @Mock JwtService jwtService;

    private OrganizerNotificationService service;

    @BeforeEach
    void setUp() {
        service = new OrganizerNotificationService(notificationRepository, organizerRepository, jwtService, 1);
    }

    @Test
    void registrationFilterReturnsPagedOrganizerNotifications() {
        authenticateOrganizer();
        OrganizerNotificationItemResponse item = new OrganizerNotificationItemResponse(
                10L, "REGISTRATION", "APPLICATION_SUBMITTED", "EVENT_APPLICATION",
                22L, "新報名", "品牌已送出報名", false, null, LocalDateTime.now());
        Set<NotificationCategory> categories = Set.of(
                NotificationCategory.APPLICATION_REVIEW,
                NotificationCategory.REGISTRATION,
                NotificationCategory.STALL_ASSIGNMENT);

        when(notificationRepository.countOrganizerNotifications(
                eq(7L), eq(categories), eq(false), any(LocalDateTime.class))).thenReturn(12L);
        when(notificationRepository.countOrganizerNotifications(
                eq(7L), eq(Set.of()), eq(true), any(LocalDateTime.class))).thenReturn(4L);
        when(notificationRepository.findOrganizerNotifications(
                eq(7L), eq(categories), eq(false), any(LocalDateTime.class), eq(10), eq(10)))
                .thenReturn(List.of(item));

        var response = service.getNotifications("Bearer token", "報名相關", 2, 99);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().unreadCount()).isEqualTo(4);
        assertThat(response.getData().notifications().getItems()).containsExactly(item);
        assertThat(response.getData().notifications().getPage()).isEqualTo(2);
        assertThat(response.getData().notifications().getPageSize()).isEqualTo(10);
    }

    @Test
    void invalidFilterAndWrongRoleAreRejectedBeforeQuery() {
        authenticateOrganizer();
        var invalidFilter = service.getNotifications("Bearer token", "攤位分配", 1, 10);
        assertThat(invalidFilter.isSuccessStatus()).isFalse();
        verify(notificationRepository, never()).findOrganizerNotifications(
                any(), any(), eq(false), any(), eq(0), eq(10));

        when(jwtService.getRole("other-token")).thenReturn("VENDOR");
        when(jwtService.isTokenValid("other-token")).thenReturn(true);
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer other-token")).thenReturn("other-token");
        var wrongRole = service.getNotifications("Bearer other-token", "全部", 1, 10);
        assertThat(wrongRole.isSuccessStatus()).isFalse();
    }

    private void authenticateOrganizer() {
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.getRole("token")).thenReturn("ORGANIZER");
        when(jwtService.getEmail("token")).thenReturn("organizer@example.com");
        when(organizerRepository.findOrganizerAccountByEmail("organizer@example.com"))
                .thenReturn(Optional.of(Map.of("userId", 7L, "role", "ORGANIZER")));
    }
}
