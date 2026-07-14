package com.example.demo.dto.response.admin;

import com.example.demo.dto.response.PageResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 
 * 管理員:主辦方詳細資料
 * 
 * @param userId            帳號id
 * @param userName          帳號名稱
 * @param role              帳號登記角色
 * @param accountStatus     帳號狀態
 * @param isGoogleBound     Google 綁定
 * @param regAt             帳號註冊時間
 * @param lastLoginAt       帳號最後登入時間
 * @param createdEventCount 主辦方建立活動總數
 * @param ongoingEventCount 主辦方未結束活動數
 * @param endedEventCount   主辦方已結束活動數
 * @param organizerName     主辦方名稱
 * @param serviceHours      主辦方營業時間 周一 - 周五 HH:mm-HH:mm
 * @param companyName       主辦方公司名稱
 * @param contactPerson     主辦方聯絡人姓名
 * @param contactPhone      主辦方聯絡電話
 * @param contactEmail      主辦方聯絡電子信箱
 * @param contactAddress    主辦方聯絡地址
 * @param taxId             主辦方統一編號
 * @param eventLogs         活動管理紀錄
 * @param loginLogs         登入紀錄
 */
@Schema(description = "管理員看到的主辦方詳細資料")
public record AdminOrgDetailDto(
        Long userId, // 用來呼叫API
        // ----------帳號資訊----------
        String userName,
        String role,
        String accountStatus,
        boolean isGoogleBound,
        String regAt,
        String lastLoginAt,
        int createdEventCount,
        int ongoingEventCount,
        int endedEventCount,
        // ----------主辦方資料----------
        String organizerName,
        String serviceHours,
        String companyName,
        String contactPerson,
        String contactPhone,
        String contactEmail,
        String contactAddress,
        String taxId,
        PageResponse<AdminOrgEventManagementDto> eventLogs,
        PageResponse<AdminUserLoginDto> loginLogs) {
}
