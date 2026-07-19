package com.example.demo.dto.request.admin;

import java.time.LocalDateTime;

import com.example.demo.enums.type.AdminOperationType;
import com.example.demo.enums.type.AdminTargetTypeForFront;

/**
 * 管理員: 操作記錄搜尋頁面 API 請求(含分頁參數)
 * @param keyWord 搜尋關鍵字，對應操作人/操作對象/操作對象Email/操作內容 :String
 * @param operationType 操作類型，參照{@link com.example.demo.enums.type.AdminOperationType}
 * @param targetType 目標類型，參照{@link com.example.demo.enums.type.AdminTargetTypeForFront}
 * @param startAt 開始日期 :LocalDateTime
 * @param endAt 結束日期 :LocalDateTime
 * @param pageNumber 頁碼，從1開始計算，為 null 時預設1 :Integer
 * @param pageSize 每頁筆數，為 null 時使用預設頁面大小 :Integer
 */
public record AdminLogsSearchRequest(
    String keyWord,
    AdminOperationType operationType,
    AdminTargetTypeForFront targetType,
    LocalDateTime startAt,
    LocalDateTime endAt,
    Integer pageNumber,
    Integer pageSize
) {}
