package com.example.demo.dto.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 管理員看到的使用者登入紀錄項目 */
@Schema(description = "使用者登入紀錄項目")
@Data
public class AdminLoginDto {
    /** 登入時間 */
    private String loginTime;
    /** 登入方式（Email/Google 等） */
    private String loginMethod;
    /** 登入狀態（成功/失敗 */
    private String loginStatus;
}
