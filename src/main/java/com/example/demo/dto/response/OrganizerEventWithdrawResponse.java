package com.example.demo.dto.response;

import java.util.List;

public record OrganizerEventWithdrawResponse(
        Long eventId,
        String workflowStatus,
        String status,
        String statusText,
        List<String> availableActions) {
}
