package com.example.demo.enums.status;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;


/**
 * 對應前端顯示的市集活動狀態。
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
    /** 活動已發布，但報名尚未開始。 */
    PUBLISHED("published", "已發布"),
    /**活動公開後狀態:活動進行中 */
    ACTIVE("active", "進行中"),
    /** 報名截止後，系統尚未完成取消未付款報名及自動選位。 */
    FINAL_CONFIRMATION("finalConfirmation", "最終名單確認中"),
    /** 自動選位完成且品牌公開時間已到，活動尚未開始。 */
    BRANDS_PUBLISHED("brandsPublished", "品牌已公開"),
    /**活動公開後狀態:活動已結束 */
    ENDED("ended", "已結束"),
    /**活動公開後狀態:主辦方提出活動下架申請 */
    UNPUBLISH_REQUESTED("pendingUnpublish", "下架申請中"),
    /**活動公開後狀態:活動下架 */
    UNPUBLISHED("unpublished", "已下架"),
    /**活動已結束款項未交付 */
    PAYMENT("payment", "款項未結清");



    @JsonValue
    private final String status;
    private final String description;

    @JsonCreator
    public static EventStatus fromStatus(String status) {
        for (EventStatus s : values()) {
            if (s.status.equalsIgnoreCase(status) || s.description.equals(status) || s.name().equals(status)) {
                return s;
            }
        }
        return null;
    }
}
