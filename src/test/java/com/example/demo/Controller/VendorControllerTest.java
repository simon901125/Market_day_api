package com.example.demo.Controller;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Service.StallService;
import com.example.demo.Service.VendorNotificationService;

@ExtendWith(MockitoExtension.class)
class VendorControllerTest {

    @Mock StallService stallService;
    @Mock VendorNotificationService vendorNotificationService;

    private VendorController controller;

    @BeforeEach
    void setUp() {
        controller = new VendorController();
        ReflectionTestUtils.setField(controller, "stallService", stallService);
        ReflectionTestUtils.setField(controller, "vendorNotificationService", vendorNotificationService);
    }

    @Test
    void notificationEndpointDelegatesAuthenticationFilterAndPagination() {
        controller.getVendorNotifications("Bearer token", "付款相關", 2, 10);

        verify(vendorNotificationService).getNotifications("Bearer token", "付款相關", 2, 10);
    }
}
