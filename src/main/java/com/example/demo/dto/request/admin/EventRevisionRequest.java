package com.example.demo.dto.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理員: 活動要求補件請求內容，供 /api/admin/events/{id}/request-revision 使用
 * @param isUnpublish 是否為下架申請退回(true: {id}為EventUnpublishRequest.id，false: {id}為Event.id) :Boolean
 * @param note 補件原因 :String
 */
@Schema(description = "管理員: 活動要求補件請求內容")
public record EventRevisionRequest(
    Boolean isUnpublish,
    String note
) {}
