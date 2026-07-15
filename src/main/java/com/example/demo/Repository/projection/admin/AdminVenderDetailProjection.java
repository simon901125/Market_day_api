package com.example.demo.Repository.projection.admin;

import java.time.LocalDateTime;

import com.example.demo.entity.User;
import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.type.Role;

/**
 * 管理員:攤主詳細頁面:帳號與品牌基本資料查詢欄位<br>
 * 最後登入時間、未結束活動數、已完成活動數為另外查詢，不包含在此projection內
 *
 * @param userId 帳號id
 * @param userName 帳號名稱(userProfile.contactName)
 * @param role 帳號登記角色
 * @param accountStatus 帳號狀態
 * @param provider 帳號登入方式
 * @param regAt 帳號註冊時間
 * @param brandName 品牌名稱
 * @param brandType 品牌類型(分類名稱)
 * @param contactPhone 品牌聯絡電話
 * @param contactEmail 品牌聯絡電子信箱
 * @param city 品牌聯絡地址-縣市
 * @param district 品牌聯絡地址-地區
 * @param address 品牌聯絡地址-地址
 *
 * @see com.example.demo.Repository.UserRepo#findVenderDetailById(Long)
 */
public record AdminVenderDetailProjection(
    // ----------帳號資訊----------
    Long userId,
    String userName,
    Role role,
    UserStatus accountStatus,
    User.Provider provider,
    LocalDateTime regAt,
    // ----------攤主資料----------
    String brandName,
    String brandType,
    String contactPhone,
    String contactEmail,
    String city,
    String district,
    String address) {}
