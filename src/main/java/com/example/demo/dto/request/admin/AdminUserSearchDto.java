package com.example.demo.dto.request.admin;

import com.example.demo.entity.User;
import com.example.demo.enums.Role;

import io.swagger.v3.oas.annotations.media.Schema;

/**管理員: 使用者搜尋頁面搜尋請求 */
@Schema(description = "管理員: 使用者搜尋頁面搜尋請求")
public record AdminUserSearchDto(
    //搜尋關鍵字，對應姓名/Email欄位
    String keyWord,
    //帳號角色
    Role role,
    //帳號狀態
    User.Status status
) {} 