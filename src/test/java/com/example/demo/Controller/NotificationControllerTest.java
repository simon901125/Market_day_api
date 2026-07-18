package com.example.demo.Controller;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Service.NotificationService;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {
    @Mock NotificationService notificationService;
    NotificationController controller;
    static final String AUTH = "Bearer token";

    @BeforeEach
    void setUp() {
        controller = new NotificationController();
        ReflectionTestUtils.setField(controller, "notificationService", notificationService);
    }

    @Test
    void markNotificationAsReadDelegatesToService() {
        controller.markNotificationAsRead(AUTH, 1L);
        verify(notificationService).markAsRead(AUTH, 1L);
    }
}
