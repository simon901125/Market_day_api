package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "品牌搜尋結果")
public class BrandSearchResponse {

    @Schema(description = "品牌總數", example = "50")
    private int totalCount;

    @Schema(description = "品牌分頁資料")
    private PageResponse<BrandSummaryResponse> brands;

    public BrandSearchResponse(PageResponse<BrandSummaryResponse> brands) {
        this.brands = brands;
        this.totalCount = brands == null ? 0 : (int) brands.getTotalItems();
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public PageResponse<BrandSummaryResponse> getBrands() {
        return brands;
    }

    public void setBrands(PageResponse<BrandSummaryResponse> brands) {
        this.brands = brands;
    }
}
