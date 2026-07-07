package com.example.demo.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**用來對應{@link com.example.demo.entity.MarketEvent}中的市集活動狀態 */
@Getter
@RequiredArgsConstructor
public enum WorkflowStatus {

    /**活動處於草稿狀態 */
    DRAFT("draft", "草稿"),
    /**活動由管理員審核中 */
    PENDING_REVIEW("pendingReview", "待審核"),
    //TODO:資料庫後續更新後要再確認
    /**管理員審核活動拒絕後，主辦方正在補件 */
    REVISION_REQUIRED("revisionRequired", "補件中"),
    /**活動地圖建置中 */
    MAP_BUILDING("mapBuilding", "地圖建置中"),
    /**活動準備公開 */
    READY_TO_PUBLISH("readyToPublish", "待發布"),
    /**活動公開 並且處於報名狀態*/
    PUBLISHED("published", ""),
    /**活動報名流程結束，準備公布參與品牌 */
    FINAL_REVIEW("finalReview", ""),
    /**主辦方提出活動下架申請 */
    UNPUBLISH_REQUESTED("pendingUnpublish", "下架申請中"),
    /**活動下架 */
    UNPUBLISHED("unpublished", "已下架"),
    /**活動取消 */
    CANCELLED("cancelled", "");

    @JsonValue
    private final String status;
    private final String description;

    @JsonCreator
    public static WorkflowStatus fromStatus(String status) {
        for (WorkflowStatus s : values()) {
            if (s.status.equals(status)) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知的狀態: " + status);
    }
}
