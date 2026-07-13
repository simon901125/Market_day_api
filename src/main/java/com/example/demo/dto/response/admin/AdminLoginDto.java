package com.example.demo.dto.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理員:使用者登入紀錄項目
 * @param loginTime 登入時間
 * @param loginMethod 登入方式（Email/Google 等）
 * @param loginStatus 登入狀態（成功/失敗)
 */
@Schema(description = "使用者登入紀錄項目")
public record AdminLoginDto(
    /** 登入時間 */
    String loginTime,
    /** 登入方式（Email/Google 等） */
    String loginMethod,
    /** 登入狀態（成功/失敗) */
    String loginStatus
) {} 
