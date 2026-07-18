package com.example.demo.dto.response;

import java.time.LocalDateTime;

public record OrganizerEventSummaryResponse(
        Long eventId,
        String eventTitle,
        String coverImageUrl,
        LocalDateTime createdAt,
        LocalDateTime eventStartAt,
        LocalDateTime eventEndAt,
        LocalDateTime registrationStartAt,
        LocalDateTime registrationEndAt,
        String locationName,
        String city,
        String district,
        String address,
        String workflowStatus,
        String status,
        String statusText,
        int capacity,
        int registeredCount,
        int pendingReviewCount,
        int paidCount,
        int selectedCount) {
}
