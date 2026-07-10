package com.example.demo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Vendor payment request")
public class VendorPaymentRequest {

    @NotBlank(message = "Application number is required")
    @Schema(description = "Application number", example = "PAYTEST-APP-001")
    private String applicationNo;

    public String getApplicationNo() {
        return applicationNo;
    }

    public void setApplicationNo(String applicationNo) {
        this.applicationNo = applicationNo;
    }
}
