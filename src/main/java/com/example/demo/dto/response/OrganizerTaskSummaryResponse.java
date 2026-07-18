package com.example.demo.dto.response;

public record OrganizerTaskSummaryResponse(
        long pendingReviewCount,
        long pendingRefundConfirmationCount,
        long pendingStallSelectionCount) {
}
