package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "主辦方付款列表查詢結果")
public class OrganizerPaymentSearchResponse {
    private final PageResponse<OrganizerPaymentSummaryResponse> payments;

    public OrganizerPaymentSearchResponse(PageResponse<OrganizerPaymentSummaryResponse> payments) {
        this.payments = payments;
    }

    public PageResponse<OrganizerPaymentSummaryResponse> getPayments() {
        return payments;
    }
}
