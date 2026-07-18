package com.example.demo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Vendor refund request")
public class VendorRefundRequest {

    @NotBlank(message = "Application number is required")
    @Schema(description = "Application number", example = "PAYTEST-APP-002")
    private String applicationNo;

    @NotBlank(message = "Refund reason is required")
    @Size(max = 255, message = "Refund reason must not exceed 255 characters")
    @Schema(description = "Refund reason", example = "TEST")
    private String reason;

    public String getApplicationNo() {
        return applicationNo;
    }

    public void setApplicationNo(String applicationNo) {
        this.applicationNo = applicationNo;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
