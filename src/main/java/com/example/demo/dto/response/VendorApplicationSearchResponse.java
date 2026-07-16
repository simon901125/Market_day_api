package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "攤主報名紀錄搜尋結果")
public class VendorApplicationSearchResponse {

    @Schema(description = "符合條件的報名總筆數", example = "12")
    private int totalCount;

    @Schema(description = "報名紀錄分頁資料")
    private PageResponse<VendorApplicationSummaryResponse> applications;

    public VendorApplicationSearchResponse(PageResponse<VendorApplicationSummaryResponse> applications) {
        this.applications = applications;
        this.totalCount = applications == null ? 0 : (int) applications.getTotalItems();
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public PageResponse<VendorApplicationSummaryResponse> getApplications() {
        return applications;
    }

    public void setApplications(PageResponse<VendorApplicationSummaryResponse> applications) {
        this.applications = applications;
    }
}
