package com.example.demo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Organizer refund review request")
public class OrganizerRefundRequest {

    @NotBlank(message = "Refund number is required")
    @Schema(description = "Refund number", example = "REF2026071715210188A95")
    private String refundNo;

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }
}