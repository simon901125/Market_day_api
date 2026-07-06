package com.example.demo.dto.response.admin;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**管理員看到的主辦方詳細資料 */
@Schema(description = "管理員看到的主辦方詳細資料")
@Data
public class AdminOrganizerDetailDto {
    /**帳號id */
    private Long userId;
    /**帳號名稱 */
    private String userName;
    /**帳號登記角色 */
    private String role;
    /**帳號登記email */
    private String email;
    /**帳號狀態 */
    private String accountStatus;
    /**帳號註冊時間 */
    private String registeredAt;
    /**帳號最後登入時間 */
    private String lastLoginAt;
    /**主辦方建立活動總數 */
    private int createdActivityCount;
    /**主辦方進行中活動數 */
    private int ongoingActivityCount;
    /**主辦方已結束活動數 */
    private int endedActivityCount;
    /**主辦方名稱 */
    private String organizerName;
    /**主辦方聯絡人姓名 */
    private String contactPerson;
    /**主辦方聯絡電話 */
    private String contactPhone;
    /**主辦方聯絡電子信箱 */
    private String contactEmail;
    /**主辦方聯絡地址 */
    private String contactAddress;
    /**主辦方公司名稱 */
    private String companyName;
    /**主辦方統一編號 */
    private String taxId;
    /**主辦方狀態 */
    private String organizerStatus;
    /**活動管理紀錄總筆數 */
    private int totalManagement;
    /**活動管理紀錄 */
    private List<EventManagementItem> managementList;
    /**登入紀錄總筆數 */
    private int totalLogin;
    /**登入紀錄 */
    private List<AdminLoginDto> loginList;

    /**活動管理紀錄項目 */
    @Data
    private class EventManagementItem {
        /** 活動名稱 */
        private String eventName;
        /** 活動日期 */
        private String eventDate;
        /** 活動狀態 */
        private String eventStatus;
        /** 報名人數（格式範例：120/150、55/55 等） */
        private String registrationCount;
    }
 
}
