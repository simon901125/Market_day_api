package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 攤主活動報名列表查詢結果，包含總筆數與分頁資料。
 */
@Schema(description = "攤主活動報名列表查詢結果")
public class VendorMarketSearchResponse {

    @Schema(description = "符合查詢條件的報名資料總數", example = "32")
    private int totalCount;

    @Schema(description = "報名列表分頁結果")
    private PageResponse<VendorMarketSummaryResponse> applications;

    public VendorMarketSearchResponse(PageResponse<VendorMarketSummaryResponse> applications) {
        this.applications = applications;
        this.totalCount = applications == null ? 0 : (int) applications.getTotalItems();
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public PageResponse<VendorMarketSummaryResponse> getApplications() {
        return applications;
    }

    public void setApplications(PageResponse<VendorMarketSummaryResponse> applications) {
        this.applications = applications;
    }
}
