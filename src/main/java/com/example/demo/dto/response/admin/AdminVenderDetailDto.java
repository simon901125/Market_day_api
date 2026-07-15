package com.example.demo.dto.response.admin;

import com.example.demo.dto.response.PageResponse;
import com.example.demo.enums.type.Role;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理員:攤主詳細頁面回傳資料
 * 
 * @param userId                帳號id
 * @param userName              帳號名稱
 * @param role                  帳號登記角色
 * @param accountStatus         帳號狀態
 * @param isGoogleBound         Google 綁定
 * @param regAt          帳號註冊時間
 * @param lastLoginAt           帳號最後登入時間
 * @param ongoingEventCount 攤主報名未結束活動數
 * @param endedEventCount   攤主已完成活動數
 * @param brandName             品牌名稱
 * @param brandType             品牌類型
 * @param owner                 負責人姓名
 * @param contactPhone          品牌聯絡電話
 * @param contactEmail          品牌聯絡電子信箱
 * @param contactAddress        品牌聯絡地址
 * @param eventRegLogs      活動報名紀錄
 * @param loginLogs             登入紀錄
 */
@Schema(description = "管理員看到的攤主詳細資料")
public record AdminVenderDetailDto(
        Long userId, // 用來呼叫API
        // ----------帳號資訊----------
        String userName,
        String role,
        String accountStatus,
        boolean isGoogleBound,
        String regAt,
        String lastLoginAt,
        int ongoingEventCount,
        int endedEventCount,
        // ----------攤主資料----------
        String brandName,
        String brandType,
        String owner,
        String contactPhone,
        String contactEmail,
        String contactAddress,
        PageResponse<AdminVenderRegDto> eventRegLogs,
        PageResponse<AdminUserLoginDto> loginLogs
    ) {}