package com.example.demo.Controller;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.Service.StallService;
import com.example.demo.dto.request.StallSelectionRequest;
import com.example.demo.dto.request.VendorStallSaveRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.MapBackedResponse;
import com.example.demo.dto.response.StallSelectionResponse;
import com.example.demo.dto.response.VendorAccountResponse;
import com.example.demo.dto.response.VendorStallMapResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "攤主專區 API", description = "提供攤主首頁、通知、報名、帳號與選位相關功能")
public class StallController {

    @Autowired
    private StallService stallService;

    @Operation(summary = "送出活動攤位選位", description = "攤主針對同一筆申請單送出一個或多個尚未選位日期的選位結果；已選位日期不可重複送出。")
    @PostMapping("/api/stalls/select")
    public ApiResponse<StallSelectionResponse> selectEventStall(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "applicationNo": "MD0101-APP01",
                      "selections": [
                        {
                          "applyDate": "2026-08-02",
                          "stallNo": "A06"
                        }
                      ]
                    }
                    """))) @Valid @RequestBody StallSelectionRequest body) {
        return stallService.selectEventStall(authorizationHeader, body);
    }

    @Operation(summary = "取得攤主帳號資訊", description = "取得目前登入攤主的帳號資料與品牌資料。")
    @GetMapping("/api/vendor/account")
    public ApiResponse<VendorAccountResponse> getVendorAccount(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return stallService.getVendorAccount(authorizationHeader);
    }

    @Operation(summary = "讀取攤主品牌資料", description = "取得目前登入攤主的品牌、聯絡資料、圖片、分類與商品資料。")
    @GetMapping("/api/vendor/stall/load")
    public ApiResponse<MapBackedResponse> loadVendorStallProfile(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return stallService.loadVendorStallProfile(authorizationHeader);
    }

    @Operation(summary = "儲存攤主品牌資料", description = "更新目前登入攤主的品牌、聯絡資料、分類與完整商品清單；不會修改大頭照或封面 URL。")
    @PostMapping(value = "/api/vendor/stall/save", consumes = "application/json")
    public ApiResponse<MapBackedResponse> saveVendorStallProfile(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody(required = false) VendorStallSaveRequest body) {
        return stallService.saveVendorStallProfile(authorizationHeader, body);
    }

    @Operation(summary = "取得攤主申請單選位地圖", description = "依申請編號取得攤主自己的選位地圖；applyDate 用於切換要查看或選位的日期。")
    @GetMapping("/api/vendor/stall-map/{applicationNo}")
    public ApiResponse<VendorStallMapResponse> getVendorStallMap(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String applicationNo,
            @RequestParam(value = "applyDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate applyDate) {
        return stallService.getVendorStallMap(authorizationHeader, applicationNo, applyDate);
    }
}
