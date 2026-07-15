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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import com.example.demo.Service.StallService;
import com.example.demo.dto.request.VendorApplicationSubmitRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.MarketSearchResponse;
import com.example.demo.dto.response.VendorApplicationSubmitResponse;
import com.example.demo.dto.response.VendorMarketDetailResponse;

@RestController
@Tag(name = "攤主專區 API", description = "取得活動報名列表、活動詳細資訊")
public class VendorController {
  @Autowired
  private StallService stallService;

  @Operation(summary = "取得活動報名列表", description = "公開查詢市集活動列表，依關鍵字、縣市、行政區、日期區間及活動狀態查詢可報名的市集活動")
  @PostMapping("/api/vendor/markets/search")
  /**
   * 取得當前活動列表，攤主可依活動名稱、報名編號、報名狀態、活動日期區間進行查詢。
   * 
   * @param keyword      關鍵字
   * @param city         城市
   * @param district     地區
   * @param status       狀態
   * @param eventStartAt 活動開始日
   * @param eventEndAt   活動結束日
   * @param page         頁數
   * @param pageSize     總頁數
   * @return
   */
  public ApiResponse<MarketSearchResponse> searchMarkets(
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "city", required = false) String city,
      @RequestParam(value = "district", required = false) String district,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "event_start_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventStartAt,
      @RequestParam(value = "event_end_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventEndAt,
      @RequestParam(value = "page", defaultValue = "1") Integer page,
      @RequestParam(value = "pageSize", defaultValue = "6") Integer pageSize) {
    // Controller 僅轉交查詢參數；資料篩選、狀態判斷與分頁由 Service 統一處理。
    return stallService.searchMarkets(
        keyword,
        city,
        district,
        status,
        eventStartAt,
        eventEndAt,
        page,
        pageSize);
  }

  /**
   * 活動單筆詳細資料。
   * 
   * @param id 活動 ID
   * @return
   */
  @Operation(summary = "取得活動報名詳細資料", description = "依活動 ID 取得已發布活動、每日剩餘攤位、設備、主辦方與交通資訊。")
  @GetMapping("/api/vendor/markets/{id}")
  public ApiResponse<VendorMarketDetailResponse> getVendorMarketDetail(
      @PathVariable Long id) {
    // PathVariable 的 id 是 market_events.id，不是 event_applications.id。
    return stallService.getVendorMarketDetail(id);
  }

  /**
   * 攤主送出活動報名資料。
   * 
   * @param authorizationHeader
   * @param body
   * @return
   */
  @Operation(summary = "活動報名送出", description = "攤主針對活動送出報名日期、車牌、備註與租借設備資料，成功後建立申請單並進入待審核狀態。")
  @PostMapping("/api/vendor/applications")
  public ApiResponse<VendorApplicationSubmitResponse> submitVendorApplication(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
          {
            "eventId": 1,
            "applyDates": ["2026-08-01", "2026-08-02"],
            "vehicleNo": "ABC-1234",
            "applicantNote": "需要靠近出入口的位置",
            "equipmentRentals": [
              {
                "eventEquipmentId": 3,
                "quantity": 1,
                "rentalUnits": 2,
                "appliances": [
                  {
                    "applianceName": "咖啡機",
                    "wattage": 800
                  }
                ]
              }
            ]
          }
          """))) @Valid @RequestBody VendorApplicationSubmitRequest body) {
    return stallService.submitVendorApplication(authorizationHeader, body);
  }

}
