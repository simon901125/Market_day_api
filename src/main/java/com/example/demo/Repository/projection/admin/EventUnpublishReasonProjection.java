package com.example.demo.Repository.projection.admin;

/**
 * 管理員:活動詳細頁面下架申請查詢欄位
 *
 * @param id 下架申請id
 * @param reason 申請原因
 *
 * @see com.example.demo.Repository.EventUnpublishRequestRepo#findLatestReasonByEventIdAndStatus(Long,
 *      com.example.demo.enums.status.UnpublishRequestStatus)
 */
public record EventUnpublishReasonProjection(Long id, String reason) {}
