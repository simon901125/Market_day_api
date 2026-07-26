package com.example.demo.Repository.projection.admin;

import com.example.demo.enums.status.WorkflowStatus;

/**
 * 管理員:活動審核操作對象查詢欄位
 *
 * @param id 活動id
 * @param workflowStatus 活動目前流程狀態
 * @param title 活動名稱
 * @param organizerId 活動主辦方(User)id
 * @param organizerContactName 活動主辦方聯絡人姓名(userProfile.contactName)，若無個人資料則為null
 *
 * @see com.example.demo.Repository.EventRepo#findApprovalStatusById(Long)
 */
public record EventApprovalProjection(
    Long id,
    WorkflowStatus workflowStatus,
    String title,
    Long organizerId,
    String organizerContactName) {}
