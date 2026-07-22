package com.example.demo.Repository.projection.admin;

import java.time.LocalDateTime;

import com.example.demo.enums.status.WorkflowStatus;

/**
 * 管理員:主辦方詳細:活動管理紀錄列表查詢欄位
 * @param eventId 活動編號
 * @param title 活動名稱
 * @param startAt 活動開始時間
 * @param endAt 活動結束時間
 * @param workflowStatus 活動流程狀態
 * @param registrationStartAt 報名開始時間
 * @param registrationEndAt 報名結束時間
 * @param brandPublicAt 品牌公開時間
 * @param maxBooths 攤位總數
 * @param paymentReceived 是否已收款
 *
 * @see com.example.demo.Repository.EventRepo#findOrgEventLogs(Long, org.springframework.data.domain.Pageable)
 */
public record AdminOrgEventLogProjection(
    Long eventId,
    String title,
    LocalDateTime startAt,
    LocalDateTime endAt,
    WorkflowStatus workflowStatus,
    LocalDateTime registrationStartAt,
    LocalDateTime registrationEndAt,
    LocalDateTime brandPublicAt,
    Integer maxBooths,
    Boolean paymentReceived
) {}
