package com.example.demo.Repository.projection.admin;

import java.time.LocalDateTime;
import java.time.LocalTime;

import com.example.demo.entity.User;
import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.type.Role;

/**
 * 管理員:主辦方詳細頁面:帳號與主辦方基本資料查詢欄位<br>
 * 最後登入時間、建立/未結束/已結束活動數為另外查詢，不包含在此projection內
 *
 * @param userId 帳號id
 * @param userName 帳號名稱(userProfile.contactName)
 * @param role 帳號登記角色
 * @param accountStatus 帳號狀態
 * @param provider 帳號登入方式
 * @param regAt 帳號註冊時間
 * @param organizerName 主辦方名稱
 * @param companyName 主辦方公司名稱
 * @param contactPhone 主辦方聯絡電話
 * @param contactEmail 主辦方聯絡電子信箱
 * @param city 主辦方聯絡地址-縣市
 * @param district 主辦方聯絡地址-地區
 * @param address 主辦方聯絡地址-地址
 * @param taxId 主辦方統一編號
 * @param serviceDays 主辦方營業星期
 * @param serviceStartTime 主辦方營業開始時間
 * @param serviceEndTime 主辦方營業結束時間
 *
 * @see com.example.demo.Repository.UserRepo#findOrganizerDetailById(Long)
 */
public record AdminOrganizerDetailProjection(
    // ----------帳號資訊----------
    Long userId,
    String userName,
    Role role,
    UserStatus accountStatus,
    User.Provider provider,
    LocalDateTime regAt,
    // ----------主辦方資料----------
    String organizerName,
    String companyName,
    String contactPhone,
    String contactEmail,
    String city,
    String district,
    String address,
    String taxId,
    String serviceDays,
    LocalTime serviceStartTime,
    LocalTime serviceEndTime) {}
