package com.example.demo.Repository.projection.admin;

import java.time.LocalDateTime;

/**
 * 管理員:攤主詳細:活動報名紀錄的退款紀錄
 * @param applicationId 所屬報名編號
 * @param refundedAt 退款完成時間(尚未完成退款時為null)
 *
 * @see com.example.demo.Repository.EventApplicationRepo#findRefunds(java.util.List)
 */
public record RefundProjection(
    Long applicationId,
    LocalDateTime refundedAt
) {}
