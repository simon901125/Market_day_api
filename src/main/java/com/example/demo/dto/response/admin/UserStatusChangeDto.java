package com.example.demo.dto.response.admin;

import com.example.demo.enums.status.UserStatus;

/**
 * 
 * 管理員: 使用者帳號狀態切換
 * @param userName 使用者姓名
 * @param userEmail 使用者登入Email
 * @param newAccountStatus 新的帳號狀態
 */
public record UserStatusChangeDto(
String userName, String userEmail, UserStatus newAccountStatus
) {}
