package com.example.demo.enums;

/**
 * WorkflowStatus
 * 市集活動狀態
 */
public enum WorkflowStatus {
    
    /**
     *活動處於草稿狀態
     */
    DRAFT,
    /**
     *活動由管理員審核中
     */
    PENDING_REVIEW,
    /**
     *活動地圖建置中
     */
    MAP_BUILDING,
    /**
     *活動準備公開
     */
    READY_TO_PUBLISH,
    /**
     *活動公開
     */
    PUBLISHED,
    /**
     *主辦方提出活動下架申請
     */
    UNPUBLISH_REQUESTED,
    /**
     *活動下架
     */
    UNPUBLISHED,
    /**
     *活動報名流程結束，準備公布參與品牌
     */
    FINAL_REVIEW,
    /**
     *活動取消
     */
    CANCELLED,
}
