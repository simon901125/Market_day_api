package com.example.demo.dto.request;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Stall selection request")
public class StallSelectionRequest {

    @NotBlank(message = "Application number is required")
    @Schema(description = "Application number", example = "MD0101-APP01")
    private String applicationNo;

    @Schema(hidden = true)
    private String stallNo;

    @Valid
    @NotEmpty(message = "Stall selections are required")
    @Schema(description = "Selections for one or more unselected application dates")
    private List<Selection> selections;

    public String getApplicationNo() {
        return applicationNo;
    }

    public void setApplicationNo(String applicationNo) {
        this.applicationNo = applicationNo;
    }

    public String getStallNo() {
        return stallNo;
    }

    public void setStallNo(String stallNo) {
        this.stallNo = stallNo;
    }

    public List<Selection> getSelections() {
        return selections;
    }

    public void setSelections(List<Selection> selections) {
        this.selections = selections;
    }

    public static class Selection {

        @NotNull(message = "Apply date is required")
        @Schema(description = "Application date", example = "2026-08-01")
        private LocalDate applyDate;

        @NotBlank(message = "Stall number is required")
        @Schema(description = "Stall number", example = "A05")
        private String stallNo;

        public LocalDate getApplyDate() {
            return applyDate;
        }

        public void setApplyDate(LocalDate applyDate) {
            this.applyDate = applyDate;
        }

        public String getStallNo() {
            return stallNo;
        }

        public void setStallNo(String stallNo) {
            this.stallNo = stallNo;
        }
    }
}
