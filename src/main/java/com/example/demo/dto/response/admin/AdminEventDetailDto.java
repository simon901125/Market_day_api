package com.example.demo.dto.response.admin;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import com.example.demo.dto.response.PageResponse;
import com.example.demo.enums.status.EventStatus;

import io.swagger.v3.oas.annotations.media.Schema;


/**
 * 
 * 管理員：活動詳細
 * @param eventId 活動id
 * @param coverImg 活動封面圖片url
 * @param eventName 活動名稱
 * @param locationName 活動地點名稱
 * @param addr 活動地址
 * @param eventStatus 活動前端顯示狀態
 * @param eventType 活動類型
 * @param description 活動介紹
 * @param registrationStartTime 報名開始時間 yyyy/MM/dd HH:mm
 * @param registrationEndTime 報名結束時間 yyyy/MM/dd HH:mm
 * @param finalListCfmTime 最終名單確認時間 yyyy/MM/dd HH:mm
 * @param eventTime 活動時間 yyyy/MM/dd - yyyy/MM/dd  HH:mm-HH:mm
 * @param organizerName 主辦方名稱
 * @param taxId 主辦方統一編號
 * @param serviceHours 主辦方營業時間 周一 - 周五 HH:mm~HH:mm
 * @param contactAddr 主辦方營業地址
 * @param contactPerson 主辦方聯絡人
 * @param contactPhone 主辦方聯絡電話
 * @param contactEmail 主辦方聯絡email
 * @param mrt 活動交通方式-捷運
 * @param bus 活動交通方式-公車
 * @param driving 活動交通方式-開車
 * @param boothSpec 攤位規格 長 * 寬
 * @param boothCount 攤位數量
 * @param boothPrice 攤位價格
 * @param boothZones 攤位分區清單 分區名稱&分區攤位數量
 * @param boothLayoutImage 攤位地圖底圖url
 * @param unpublishRequestId 下架申請id，僅當eventStatus=UNPUBLISH_REQUESTED時有值，否則為null
 * @param unpublishReason 下架申請原因，僅當eventStatus=UNPUBLISH_REQUESTED時有值，否則為null
 * @param unpublishRequestedAt 下架申請時間 yyyy/MM/dd HH:mm，僅當eventStatus=UNPUBLISH_REQUESTED時有值，否則為null
 * @param logs 活動狀態logs
 */
@Schema(description = "管理員活動詳細頁面")
public record AdminEventDetailDto(
    Long eventId,
    //----------活動基礎狀態----------
    String coverImg,
    String eventName,
    String locationName,
    String addr,
    EventStatus eventStatus,
    String eventType,
    String description,
    
    //----------活動時間流程----------
    String registrationStartTime,
    String registrationEndTime,
    String finalListCfmTime,
    String eventTime,

    //----------活動主辦方資料----------
    String organizerName,
    String taxId,
    String serviceHours,
    String contactPerson,
    String contactPhone,
    String contactEmail,
    String contactAddr,

    //----------活動交通方式----------
    String mrt,
    String bus,
    String driving,

    //----------活動攤位資訊----------
    String boothSpec,
    int boothCount,
    BigDecimal boothPrice,
    List<BoothZone> boothZones,
    String boothLayoutImage,

    //----------活動下架申請----------
    Long unpublishRequestId,
    String unpublishReason,
    String unpublishRequestedAt,

    //----------活動狀態Logs----------
    PageResponse<StatusLog> logs
) {}

