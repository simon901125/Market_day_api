package com.example.demo.dto.response.admin;

import com.example.demo.dto.response.PageResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 
 * 管理員:主辦方詳細資料
 * 
 * @param userId               帳號id
 * @param userName             帳號名稱
 * @param role                 帳號登記角色
 * @param accountStatus        帳號狀態
 * @param googleBind           Google 綁定
 * @param registeredAt         帳號註冊時間
 * @param lastLoginAt          帳號最後登入時間
 * @param createdActivityCount 主辦方建立活動總數
 * @param ongoingActivityCount 主辦方未結束活動數
 * @param endedActivityCount   主辦方已結束活動數
 * @param organizerName        主辦方名稱
 * @param organizerStatus      主辦方狀態
 * @param companyName          主辦方公司名稱
 * @param contactPerson        主辦方聯絡人姓名
 * @param contactPhone         主辦方聯絡電話
 * @param contactEmail         主辦方聯絡電子信箱
 * @param contactAddress       主辦方聯絡地址
 * @param taxId                主辦方統一編號
 * @param managementList       活動管理紀錄
 * @param loginLogs            登入紀錄
 */
@Schema(description = "管理員看到的主辦方詳細資料")
public record AdminOrganizerDetailDto(
    //----------帳號資訊----------
    Long userId,
    String userName,
    String role,
    String accountStatus,
    boolean googleBind,
    String registeredAt,
    String lastLoginAt,
    int createdActivityCount,
    int ongoingActivityCount,
    int endedActivityCount,
    //----------主辦方資料----------
    String organizerName,
    String organizerStatus,
    String companyName,
    String contactPerson,
    String contactPhone,
    String contactEmail,
    String contactAddress,
    String taxId,
    PageResponse<AdminEventManagementDto> managementList,
    PageResponse<AdminLoginDto> loginLogs
) {}
