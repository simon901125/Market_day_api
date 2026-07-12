package com.example.demo.dto.request.admin;

import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.type.Role;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理員: 使用者搜尋頁面搜尋條件
 * @param keyWord 搜尋關鍵字，對應姓名/Email欄位 :String
 * @param role 帳號角色 :Role
 * @param status 帳號狀態 :UserStatus
 */
@Schema(description = "管理員: 使用者搜尋頁面搜尋條件")
public record AdminUserSearchDto(
    String keyWord,
    Role role,
    UserStatus status
) {} 