package com.example.demo.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Service.StallService;
import com.example.demo.Service.VendorDashboardService;
import com.example.demo.Service.VendorNotificationService;
import com.example.demo.dto.response.ApiResponse;

@ExtendWith(MockitoExtension.class)
class VendorControllerTest {

    @Mock StallService stallService;
    @Mock VendorNotificationService vendorNotificationService;
    @Mock VendorDashboardService vendorDashboardService;

    private VendorController controller;

    @BeforeEach
    void setUp() {
        controller = new VendorController();
        ReflectionTestUtils.setField(controller, "stallService", stallService);
        ReflectionTestUtils.setField(controller, "vendorNotificationService", vendorNotificationService);
        ReflectionTestUtils.setField(controller, "vendorDashboardService", vendorDashboardService);
    }

    @Test
    void notificationEndpointDelegatesAuthenticationFilterAndPagination() {
        controller.getVendorNotifications("Bearer token", "付款相關", 2, 10);

        verify(vendorNotificationService).getNotifications("Bearer token", "付款相關", 2, 10);
    }

    @Test
    void dashboardEndpointDelegatesAuthorizationHeader() {
        controller.initVendorDashboard("Bearer token");

        verify(vendorDashboardService).initDashboard("Bearer token");
    }

    @Test
    void cancelApplicationEndpointDelegatesAuthorizationHeaderAndApplicationId() {
        when(stallService.cancelVendorApplication("Bearer token", 12L))
                .thenReturn(ApiResponse.success("報名取消成功"));

        var response = controller.cancelVendorApplication("Bearer token", 12L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(stallService).cancelVendorApplication("Bearer token", 12L);
    }
}
