package com.example.demo.dto.response;

public class OrganizerEventSearchResponse {
    private int totalCount;
    private PageResponse<OrganizerEventSummaryResponse> events;

    public OrganizerEventSearchResponse(
            PageResponse<OrganizerEventSummaryResponse> events) {
        this.events = events;
        this.totalCount = events == null ? 0 : (int) events.getTotalItems();
    }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    public PageResponse<OrganizerEventSummaryResponse> getEvents() { return events; }
    public void setEvents(PageResponse<OrganizerEventSummaryResponse> events) { this.events = events; }
}
