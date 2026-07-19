package com.example.demo.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record OrganizerEventPublishResponse(
        Long eventId,
        String workflowStatus,
        String status,
        String statusText,
        LocalDateTime publicInfoAt,
        List<String> availableActions,
        Integer expectedStallCount,
        Integer actualStallCount,
        List<String> missingFields) {
}
