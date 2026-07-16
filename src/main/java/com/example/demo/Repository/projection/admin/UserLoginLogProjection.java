package com.example.demo.Repository.projection.admin;

import java.time.LocalDateTime;

/**
 * 管理員:使用者詳細:登入紀錄查詢欄位
 * @param loginTime 登入時間
 * @param path 登入API路徑
 * @param statusCode 回應狀態碼
 *
 * @see com.example.demo.Repository.RequestLogRepo#findUserLoginLogs(Long, java.util.List, org.springframework.data.domain.Pageable)
 */
public record UserLoginLogProjection(
    LocalDateTime loginTime,
    String path,
    Integer statusCode
) {}
