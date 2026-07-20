package com.example.demo.dto.response;

import java.util.List;

public record VendorDashboardInitResponse(
    boolean needsProfile,
    String guideMessage,
    String name,
    long pendingReviewCount,
    long pendingPaymentCount,
    long pendingStallSelectionCount,
    long pendingRefundCount,
    List<VendorNotificationItemResponse> notifications) {

    /**
     * Backward-compatible form used by the stall service's profile setup check.
     */
    public VendorDashboardInitResponse(boolean needsProfileSetup) {
        this(needsProfileSetup, null, null, 0, 0, 0, 0, List.of());
    }

    /**
     * Retains the accessor name used before the dashboard response was expanded.
     */
    public boolean needsProfileSetup() {
        return needsProfile;
    }
}
