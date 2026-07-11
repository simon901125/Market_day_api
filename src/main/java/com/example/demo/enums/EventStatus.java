package com.example.demo.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;


/**
 * 對應資前端顯示的市集活動狀態，包含草稿、待審核、補件中、地圖建置中、待發布、報名中、已額滿、品牌已公開、進行中、已結束、下架申請中、已下架
 */
@Getter
@RequiredArgsConstructor
public enum EventStatus {

    /**活動處於草稿狀態 */
    DRAFT("draft", "草稿"),
    /**活動由管理員審核中 */
    PENDING_REVIEW("pendingReview", "待審核"),
    /**活動主辦方正在補件 */
    REVISION_REQUIRED("revisionRequired", "補件中"),
    /**活動地圖建置中 */
    MAP_BUILDING("mapBuilding", "地圖建置中"),
    /**活動準備公開 */
    READY_TO_PUBLISH("readyToPublish", "待發布"),
    /**活動公開後狀態:開放攤位報名中 */
    REGISTRATION_OPEN("registrationOpen", "報名中"), 
    /**活動公開後狀態:攤位已額滿 */
    FULL("full", "已額滿"),
    /**活動公開後狀態:活動參與品牌已公開 */
    PUBLISHED("published", "品牌已公開"),
    /**活動公開後狀態:活動進行中 */
    ACTIVE("active", "進行中"),
    /**活動公開後狀態:活動已結束 */
    ENDED("ended", "已結束"),
    /**活動公開後狀態:主辦方提出活動下架申請 */
    UNPUBLISH_REQUESTED("pendingUnpublish", "下架申請中"),
    /**活動公開後狀態:活動下架 */
    UNPUBLISHED("unpublished", "已下架");


    @JsonValue
    private final String status;
    private final String description;

    @JsonCreator
    public static EventStatus fromStatus(String status) {
        for (EventStatus s : values()) {
            if (s.status.equals(status)) {
                return s;
            }
        }
        return null;
    }
}
