package com.example.demo.Controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.Service.MarketEventService;
import com.example.demo.dto.request.MarketSearchRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.MarketEventCardResponse;
import com.example.demo.dto.response.MarketEventDetailResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Market API", description = "提供市集活動查詢與篩選功能")
public class MarketController {

    @Autowired
    private MarketEventService marketEventService;

    @Operation(summary = "取得市集活動列表及取得歷史活動列表", description = "依關鍵字、縣市、活動狀態、日期區間、分類與活動類型查詢市集活動列表。")
    @PostMapping("/api/markets/search")
    public ApiResponse<List<MarketEventCardResponse>> searchMarkets(
            @RequestBody MarketSearchRequest request) {
        return marketEventService.searchMarkets(request);
    }

    @Operation(summary = "取得市集活動詳細資料", description = "依市集活動 ID 取得已發布且審核通過的市集活動詳細資料。")
    @GetMapping("/api/markets/{id}")
    public ApiResponse<MarketEventDetailResponse> getMarketDetail(@PathVariable Long id) {
        return marketEventService.getMarketDetail(id);
    }
}
