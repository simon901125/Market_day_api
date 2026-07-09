package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Organizer equipment event search response")
public class OrganizerEquipmentSearchResponse {

    @Schema(description = "Total event count", example = "7")
    private int totalCount;

    @Schema(description = "Equipment event summary page")
    private PageResponse<OrganizerEquipmentSummaryResponse> events;

    public OrganizerEquipmentSearchResponse(PageResponse<OrganizerEquipmentSummaryResponse> events) {
        this.events = events;
        this.totalCount = events == null ? 0 : (int) events.getTotalItems();
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public PageResponse<OrganizerEquipmentSummaryResponse> getEvents() {
        return events;
    }

    public void setEvents(PageResponse<OrganizerEquipmentSummaryResponse> events) {
        this.events = events;
    }
}
