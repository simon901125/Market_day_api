package com.example.demo.Repository.projection.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.example.demo.enums.status.WorkflowStatus;


/**
 *
 * 管理員後台：活動詳細（JPQL constructor expression 用的 raw projection）。<br>
 * 攤位分區清單（eventStallZones）為一對多關聯，無法併入同一列建構子表達式，需另外查詢。
 *
 * @param eventId 活動ID
 * @param eventName 活動名稱
 * @param startAt 活動開始日期時間
 * @param endAt 活動結束日期時間
 * @param brandPublicAt 參與活動的品牌名單公開時間
 * @param locationName 活動地點名稱
 * @param city 縣市
 * @param district 地區
 * @param address 地址
 * @param workflowStatus 活動流程狀態
 * @param coverImg 活動封面圖片url
 * @param description 活動介紹
 * @param regStartAt 報名開始時間
 * @param regEndAt 報名結束時間
 * @param publicInfoAt 最終名單確認時間
 * @param maxBooths 攤位數量
 * @param boothFee 攤位價格
 * @param stallWidth 攤位寬度
 * @param stallLength 攤位長度
 * @param mapImg 攤位地圖底圖url
 * @param organizerName 主辦方名稱
 * @param contactName 主辦方聯絡人
 * @param contactPhone 主辦方聯絡電話
 * @param contactEmail 主辦方聯絡email
 * @param contactCity 主辦方營業地址-縣市
 * @param contactDist 主辦方營業地址-地區
 * @param contactAddr 主辦方營業地址-地址
 * @param texId 主辦方統一編號
 * @param serviceDays 主辦方營業星期
 * @param serviceStartTime 主辦方營業開始時間
 * @param serviceEndTime 主辦方營業結束時間
 * @param mrt 捷運交通資訊
 * @param bus 公車交通資訊
 * @param driving 開車交通資訊
 * @param paymentReceived 是否已收款
 *
 * @see com.example.demo.Repository.EventRepo#findEventDetailById(Long)
*/
public record AdminEventDetailProjection(
    Long eventId,
    String eventName,
    LocalDateTime startAt,
    LocalDateTime endAt,
    LocalDateTime brandPublicAt,
    String locationName,
    String city,
    String district,
    String address,
    WorkflowStatus workflowStatus,
    String coverImg,
    String description,
    LocalDateTime regStartAt,
    LocalDateTime regEndAt,
    LocalDateTime publicInfoAt,
    Integer maxBooths,
    BigDecimal boothFee,
    BigDecimal stallWidth,
    BigDecimal stallLength,
    String mapImg,
    String organizerName ,
    String contactName,
    String contactPhone,
    String contactEmail,
    String contactCity,
    String contactDist,
    String contactAddr,
    String texId,
    String serviceDays,
    LocalTime serviceStartTime,
    LocalTime serviceEndTime,
    String mrt,
    String bus,
    String driving,
    Boolean paymentReceived
) {}
