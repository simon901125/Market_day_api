package com.example.demo.dto.response;

import java.util.List;

public record VendorDashboardInitResponse(
        boolean needsProfile,
        String guideMessage,
        String name,
        long pendingReviewCount,
        long pendingPaymentCount,
        long pendingStallSelectionCount,
        List<VendorNotificationItemResponse> notifications) {
}
