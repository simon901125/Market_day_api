package com.example.demo.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "主辦方攤位管理活動搜尋結果")
public class OrganizerStallEventSearchResponse {

    @Schema(description = "活動總筆數", example = "7")
    private int totalCount;

    @Schema(description = "活動列表")
    private List<OrganizerStallEventSummaryResponse> events;

    public OrganizerStallEventSearchResponse(List<OrganizerStallEventSummaryResponse> events) {
        this.events = events;
        this.totalCount = events == null ? 0 : events.size();
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public List<OrganizerStallEventSummaryResponse> getEvents() {
        return events;
    }

    public void setEvents(List<OrganizerStallEventSummaryResponse> events) {
        this.events = events;
    }
}
