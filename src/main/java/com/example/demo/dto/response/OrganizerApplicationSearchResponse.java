package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "主辦方報名列表查詢結果")
public class OrganizerApplicationSearchResponse {

    @Schema(description = "符合查詢條件的報名資料總數", example = "12")
    private int totalCount;

    @Schema(description = "報名列表分頁結果")
    private PageResponse<OrganizerApplicationSummaryResponse> applications;

    public OrganizerApplicationSearchResponse(PageResponse<OrganizerApplicationSummaryResponse> applications) {
        this.applications = applications;
        this.totalCount = applications == null ? 0 : (int) applications.getTotalItems();
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public PageResponse<OrganizerApplicationSummaryResponse> getApplications() {
        return applications;
    }

    public void setApplications(PageResponse<OrganizerApplicationSummaryResponse> applications) {
        this.applications = applications;
    }
}
