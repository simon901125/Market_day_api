package com.example.demo.dto.request.admin;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
/**管理員: 操作記錄搜尋頁面搜尋請求 */
@Schema(description = "管理員: 操作記錄搜尋頁面搜尋請求")
public record AdminLogSearchDto(
    //搜尋關鍵字，對應操作人/操作內容
    String keyWord,
    //操作類型
    //TODO:後續考慮改成Enum
    String actionType,
    //開始日期 yyyy-MM-dd
    LocalDateTime startAt,
    //結束日期 yyyy-MM-dd
    LocalDateTime endAt
) {}