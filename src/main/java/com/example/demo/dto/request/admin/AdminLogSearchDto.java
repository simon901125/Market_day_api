package com.example.demo.dto.request.admin;

import java.time.LocalDateTime;

import com.example.demo.enums.type.AdminOperationType;
import com.example.demo.enums.type.AdminTargetTypeForFront;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 管理員: 操作記錄搜尋頁面搜尋條件
 * @param keyWord 搜尋關鍵字，對應操作人/操作對象/操作對象Email/操作內容 :String
 * @param operationType 操作類型，參照{@link com.example.demo.enums.type.AdminOperationType}
 * @param targetType 目標類型，參照{@link com.example.demo.enums.type.AdminTargetTypeForFront}
 * @param startAt 開始日期 :LocalDateTime
 * @param endAt 結束日期 :LocalDateTime
 */
@Schema(description = "管理員: 操作記錄搜尋頁面搜尋請求")
public record AdminLogSearchDto(
    String keyWord,
    AdminOperationType operationType,
    AdminTargetTypeForFront targetType,
    LocalDateTime startAt,
    LocalDateTime endAt
) {}