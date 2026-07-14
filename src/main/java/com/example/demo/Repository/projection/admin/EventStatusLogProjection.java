package com.example.demo.Repository.projection.admin;

import java.time.LocalDateTime;

import com.example.demo.enums.type.Role;

/**
 * 管理員:活動詳細:活動狀態變動紀錄列表查詢欄位
 * @param reqAt 狀態變更請求時間
 * @param newStatus 變更後的活動流程狀態(WorkflowStatus名稱)
 * @param role 操作人員角色
 * @param orgName 主辦方聯絡人姓名(操作人員為主辦方時使用)
 * @param adminName 管理員名稱(操作人員為管理員時使用)
 *
 * @see com.example.demo.Repository.StatusLogRepo#findEventStatusLogs(Long, org.springframework.data.domain.Pageable)
 */
public record EventStatusLogProjection(
    LocalDateTime reqAt,
    String newStatus,
    Role role,
    String orgName,
    String adminName
) {}
