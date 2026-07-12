package com.example.demo.dto.response.admin;

import com.example.demo.enums.type.AdminOperationType;
import com.example.demo.enums.type.AdminTargetTypeForFront;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 
 * 管理員:系統操作紀錄
 * @param operator 操作人員名稱
 * @param operationType 操作類型，參照{@link com.example.demo.enums.type.AdminOperationType}
 * @param targetType 操作對象類型，參照{@link com.example.demo.enums.type.AdminTargetTypeForFront}
 * @param targetName 操作對象
 * @param email 操作對象email
 * @param createdAt 系統操作時間 yyyy-MM-dd HH:mm
 * @param content 操作說明
 */
@Schema(description = "管理員:系統操作紀錄")
public record AdminLogDto(
    String operator, 
    AdminOperationType operationType, 
    AdminTargetTypeForFront targetType,
    String targetName, 
    String email,
    String createdAt, 
    String content
) {} 

