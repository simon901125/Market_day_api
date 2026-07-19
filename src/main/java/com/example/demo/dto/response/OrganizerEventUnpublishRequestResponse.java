package com.example.demo.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record OrganizerEventUnpublishRequestResponse(
        Long eventId,
        Long unpublishRequestId,
        String workflowStatus,
        String status,
        String statusText,
        String reason,
        LocalDateTime requestedAt,
        List<String> availableActions) {
}
