package com.example.demo.dto.request.admin;

import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.type.Role;

/**
 * 
 * 管理員: 使用者搜尋頁面 API 請求(含分頁參數)
 * @param keyWord 搜尋關鍵字，對應姓名/Email欄位 :String
 * @param role 帳號角色 :Role
 * @param status 帳號狀態 :UserStatus
 * @param pageNumber 頁碼，從1開始計算，為 null 時預設1 :Integer
 * @param pageSize 每頁筆數，為 null 時使用預設頁面大小 :Integer
 */
public record AdminUserSearchRequest(
        String keyWord,
        Role role,
        UserStatus status,
        Integer pageNumber,
        Integer pageSize
    ) {}
