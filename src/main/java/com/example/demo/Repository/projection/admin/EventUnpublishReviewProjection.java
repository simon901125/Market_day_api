package com.example.demo.Repository.projection.admin;

import java.time.LocalDateTime;

import com.example.demo.enums.status.UnpublishRequestStatus;
import com.example.demo.enums.status.WorkflowStatus;

/**
 * 管理員後台: 下架申請退回審核所需資訊投影
 * @param requestStatus 下架申請單狀態
 * @param eventId 活動id
 * @param eventStatus 活動流程狀態
 * @param eventName 活動名稱
 * @param brandPublicAt 品牌公開時間，null表示尚未公開過
 * @param userId 活動主辦方(擁有者)使用者id
 * @see com.example.demo.Repository.EventUnpublishRequestRepo#findReviewInfoById(Long)
 */
public record EventUnpublishReviewProjection(
    UnpublishRequestStatus requestStatus,
    Long eventId,
    WorkflowStatus eventStatus,
    String eventName,
    LocalDateTime brandPublicAt,
    Long userId) {}
