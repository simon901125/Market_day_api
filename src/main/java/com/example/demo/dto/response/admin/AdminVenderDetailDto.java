package com.example.demo.dto.response.admin;

import com.example.demo.dto.response.PageResponse;
import com.example.demo.enums.type.Role;

import io.swagger.v3.oas.annotations.media.Schema;


/**
 * 管理員:攤主詳細頁面回傳資料
 * @param userId 帳號id
 * @param userName 帳號名稱
 * @param role 帳號登記角色
 * @param accountStatus 帳號狀態
 * @param registeredAt 帳號註冊時間
 * @param lastLoginAt 帳號最後登入時間
 * @param noCompletedEventCount 攤主報名未結束活動次數
 * @param completedEventCount 攤主已完成活動數
 * @param brandName 品牌名稱
 * @param brandType 品牌類型
 * @param owner 負責人姓名
 * @param contactPhone 品牌聯絡電話
 * @param contactEmail 品牌聯絡電子信箱
 * @param contactAddress 品牌聯絡地址
 * @param registrationList 活動報名紀錄
 * @param loginList 登入紀錄
 * @param googleBind google綁定
 */
@Schema(description = "管理員看到的攤主詳細資料")
public record AdminVenderDetailDto(
    //----------帳號資訊----------
     Long userId,
     String userName,
     Role role,
     String accountStatus,
     boolean googleBind,
     String registeredAt,
     String lastLoginAt,
     int noCompletedEventCount,
     int completedEventCount,
     //----------攤主資料----------
     String brandName,
     String brandType,
     String contactPhone,
     String owner,
     String contactEmail,
     String contactAddress,
     PageResponse<AdminVenderRegDto> registrationList,
     PageResponse<AdminLoginDto> loginList
) {} 