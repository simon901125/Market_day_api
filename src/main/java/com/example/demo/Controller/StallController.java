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
import com.example.demo.dto.request.VendorProductSaveRequest;
import com.example.demo.dto.request.VendorStallSaveRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.MapBackedResponse;
import com.example.demo.dto.response.StallSelectionResponse;
import com.example.demo.dto.response.VendorAccountResponse;
import com.example.demo.dto.response.VendorMarketSearchResponse;
import com.example.demo.dto.response.VendorStallMapResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "攤主後台 API", description = "提供攤主帳號、報名紀錄、攤位地圖與選位功能")
public class StallController {

  @Autowired
  private StallService stallService;

  @Operation(summary = "送出活動攤位選位", description = "攤主針對同一筆報名單送出一個或多個活動日期的選位結果。")
  @PostMapping("/api/stalls/select")
  /**
   * 攤主送出活動選位
   * 
   * @param authorizationHeader
   * @param body
   * @return
   */
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

  @Operation(summary = "取得攤主申請單選位地圖", description = "依報名編號取得攤主自己的選位地圖；applyDate 用於切換要查看或選位的日期。")
  @GetMapping("/api/vendor/stall-map/{applicationNo}")

  /***
   * 取得攤主指定報名單的選位地圖。
   * 
   * @param authorizationHeader
   * @param applicationNo
   * @param applyDate
   * @return
   */
  public ApiResponse<VendorStallMapResponse> getVendorStallMap(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable String applicationNo,
      @RequestParam(value = "applyDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate applyDate) {
    return stallService.getVendorStallMap(authorizationHeader, applicationNo, applyDate);
  }

  /**
   * 取得當前活動列表，攤主可依活動名稱、報名編號、報名狀態、活動日期區間進行查詢。
   * 
   * @param eventTitle    活動名稱
   * @param applicationNo 報名編號
   * @param status        報名狀態
   * @param eventStartAt  活動開始日期
   * @param eventEndAt    活動結束日期
   * @param page          頁碼
   * @param pageSize      每頁筆數
   * @return
   */
  @Operation(summary = "取得我的報名紀錄", description = "取得我的報名紀錄")
  @PostMapping("/api/vendor/applications/search")
  public ApiResponse<VendorMarketSearchResponse> searchVendorMarkets(
      // 接收前端放在 Header 裡面的 JWT
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(value = "eventTitle", required = false) String eventTitle,
      @RequestParam(value = "applicationNo", required = false) String applicationNo,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "event_start_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventStartAt,
      @RequestParam(value = "event_end_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventEndAt,
      @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
      @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
    return stallService.searchVendorMarkets(
        authorizationHeader,
        eventTitle,
        applicationNo,
        status,
        eventStartAt,
        eventEndAt,
        page,
        pageSize);
  }

}

  
  
  
      
    
  

  
  
  
      
      
    
  

  
  
  
      
      
    
  

  
  
  
      
      
      
    
  

  
  
  
      
      
    
  

  