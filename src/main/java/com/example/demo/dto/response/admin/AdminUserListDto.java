package com.example.demo.dto.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 
 * 管理員:使用者管理列表項目
 * @param id 使用者id
 * @param role 使用者角色 (參照 {@link com.example.demo.enums.type.Role})
 * @param name 使用者名稱
 * @param status 使用者帳號狀態(參照 {@link com.example.demo.entity.User.Status})
 * @param email 使用者email
 * @param regAt 註冊時間 yyyy/MM/dd HH:mm 
 * @param lastLoginAt 最後登入時間 yyyy/MM/dd HH:mm
 * 
 */
@Schema(description = "管理員:使用者管理列表項目")
public record AdminUserListDto(
    Long id,
    String role,
    String name,
    String status,
    String email,
    String regAt,
    String lastLoginAt
) {} 
