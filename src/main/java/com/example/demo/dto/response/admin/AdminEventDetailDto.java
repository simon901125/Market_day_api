package com.example.demo.dto.response.admin;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**管理員：活動詳細 */
@Data
@Schema(description = "管理員活動詳細頁面")
public class AdminEventDetailDto {
    /**活動名稱 */
    private String eventName;
    /**活動類型 */
    private String eventType;
    /**活動時間 yyyy-MM-dd - yyyy-MM-dd  HH:mm - HH:mm*/
    private String eventTime;
    /**活動地點名稱 */
    private String locationName;
    /**活動地址 */
    private String addr;
    /**活動描述 */
    private String description;
    /**報名開始時間 yyyy-MM-dd HH:mm*/
    private String registrationStartTime;
    /**報名結束時間 yyyy-MM-dd HH:mm*/
    private String registrationEndTime;
    /**最終名單確認時間 yyyy-MM-dd HH:mm*/
    private String finalListConfirmation;
    /**活動時間 yyyy-MM-dd - yyyy-MM-dd  HH:mm - HH:mm*/
    private String activityTime;
    /**主辦方名稱 */
    private String organizerName;
    /**主辦方聯絡人 */
    private String contactPerson;
    /**主辦方聯絡電話 */
    private String contactPhone;
    /**主辦方聯絡email */
    private String email;
    /**主辦方營業地址 */
    private String address;
    /**主辦方統一編號 */
    private String taxId;
    /**主辦方營業時間 周一 ~ 周五 HH:mm - HH:mm */
    private String serviceHours;
    /**活動交通方式-捷運 */
    private String mrt;
    /**活動交通方式-公車 */
    private String bus;
    /**活動交通方式-開車 */
    private String drivingDirections;
    /**攤位規格 長 * 寬 */
    private String boothSpec;
    /**攤位數量 */
    private String boothCount;
    /**攤位價格 */
    private String boothPrice;
    /**攤位分區清單 */
    private List<BoothZone> boothZones;
    /**攤位地圖底圖url */
    private String boothLayoutImage;
    /**活動狀態logs */
    private List<StatusLog> logs;

}
