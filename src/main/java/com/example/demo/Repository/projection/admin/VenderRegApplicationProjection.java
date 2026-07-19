package com.example.demo.Repository.projection.admin;

import com.example.demo.enums.status.PaymentStatus;
import com.example.demo.enums.status.ReviewStatus;

/**
 * 管理員:攤主詳細:活動報名紀錄列表查詢欄位
 * @param applicationId 報名編號
 * @param eventId 活動編號
 * @param eventName 活動名稱
 * @param reviewStatus 審核狀態
 * @param paymentStatus 付款狀態
 * @param isCancelled 是否已取消
 *
 * @see com.example.demo.Repository.EventApplicationRepo#findVenderRegApplications(Long, org.springframework.data.domain.Pageable)
 */
public record VenderRegApplicationProjection(
    Long applicationId,
    Long eventId,
    String eventName,
    ReviewStatus reviewStatus,
    PaymentStatus paymentStatus,
    Boolean isCancelled
) {}
