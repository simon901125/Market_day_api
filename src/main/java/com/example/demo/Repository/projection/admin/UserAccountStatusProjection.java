package com.example.demo.Repository.projection.admin;

import com.example.demo.enums.status.UserStatus;

/**
 * 管理員:使用者帳號狀態變更(停用/復原)操作對象查詢欄位
 *
 * @param id 使用者帳號id
 * @param status 使用者目前帳號狀態
 * @param email 使用者email
 * @param contactName 使用者聯絡人姓名(userProfile.contactName)，若無個人資料則為null
 *
 * @see com.example.demo.Repository.UserRepo#findAccountStatusById(Long)
 */
public record UserAccountStatusProjection(
    Long id,
    UserStatus status,
    String email,
    String contactName) {}
