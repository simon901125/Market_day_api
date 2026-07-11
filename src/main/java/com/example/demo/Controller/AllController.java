package com.example.demo.Controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.Service.BrandService;
import com.example.demo.Service.StallService;
import com.example.demo.dto.request.BrandSearchRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.BrandDetailResponse;
import com.example.demo.dto.response.BrandSearchResponse;
import com.example.demo.dto.response.EventStallStatusResponse;
import com.example.demo.dto.response.MapBackedResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "一般使用者 API", description = "提供一般使用者不需登入即可查詢的公開 API")
public class AllController {

    @Autowired
    private StallService stallService;

    @Autowired
    private BrandService brandService;

    @Operation(summary = "查詢活動攤位狀態", description = "依活動 ID 與可選日期查詢公開攤位狀態")
    @GetMapping("/api/eventsMap/{eventId}/stallsStatus")
    public ApiResponse<List<EventStallStatusResponse>> getPublicEventStallsStatus(
            @Parameter(description = "活動 ID", example = "1")
            @PathVariable Long eventId,
            @Parameter(description = "申請日期，格式 yyyy-MM-dd；未提供時查詢全部日期", example = "2026-08-01")
            @RequestParam(value = "applyDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate applyDate) {
        return stallService.getPublicEventStallsStatus(eventId, applyDate);
    }

    @Operation(summary = "取得品牌搜尋下拉選項", description = "提供品牌搜尋頁面的分類名稱與市集名稱選項")
    @GetMapping("/api/brands/scroll-options")
    public ApiResponse<MapBackedResponse> getBrandScrollOptions() {
        return brandService.getBrandScrollOptions();
    }

    @Operation(summary = "搜尋品牌列表", description = "依關鍵字、分類名稱、參與市集名稱搜尋品牌")
    @GetMapping("/api/brands/search")
    public ApiResponse<BrandSearchResponse> searchBrands(
            @Parameter(description = "關鍵字，可搜尋品牌、簡述、介紹與商品內容")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "分類名稱")
            @RequestParam(value = "categoryName", required = false) String categoryName,
            @Parameter(description = "參與市集名稱")
            @RequestParam(value = "marketName", required = false) String marketName,
            @Parameter(description = "頁碼，預設 1", example = "1")
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(description = "每頁筆數，最多 6", example = "6")
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        BrandSearchRequest request = new BrandSearchRequest(
                keyword,
                categoryName,
                marketName,
                page,
                pageSize);
        return brandService.searchBrands(request);
    }

    @Operation(summary = "取得品牌詳情", description = "提供一般使用者查看品牌介紹、代表商品與參與市集紀錄")
    @GetMapping("/api/brands/{id}")
    public ApiResponse<BrandDetailResponse> getBrandDetail(
            @Parameter(description = "品牌 ID", example = "1")
            @PathVariable Long id) {
        return brandService.getBrandDetail(id);
    }
}
