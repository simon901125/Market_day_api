package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "主辦方帳務列表查詢結果")
public class OrganizerAccountingSearchResponse {

    @Schema(description = "帳務列表筆數", example = "12")
    private int totalCount;

    @Schema(description = "帳務列表分頁結果")
    private PageResponse<OrganizerAccountingSummaryResponse> accounts;

    public OrganizerAccountingSearchResponse(PageResponse<OrganizerAccountingSummaryResponse> accounts) {
        this.accounts = accounts;
        this.totalCount = accounts == null ? 0 : (int) accounts.getTotalItems();
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public PageResponse<OrganizerAccountingSummaryResponse> getAccounts() {
        return accounts;
    }

    public void setAccounts(PageResponse<OrganizerAccountingSummaryResponse> accounts) {
        this.accounts = accounts;
    }
}
