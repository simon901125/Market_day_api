package com.example.demo.dto.response.admin;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**管理員看到的攤主詳細資料 */
@Schema(description = "管理員看到的攤主詳細資料")
@Data
public class AdminVenderDetailDto {
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
    /**攤主報名活動總次數 */
    private int registrationCount;
    /**攤主已完成活動數 */
    private int completedEventCount;
    /**品牌名稱 */
    private String brandName;
    /**品牌類型 */
    private String brandType;
    /**負責人姓名 */
    private String owner;
    /**品牌聯絡電話 */
    private String contactPhone;
    /**品牌聯絡電子信箱 */
    private String contactEmail;
    /**品牌聯絡地址 */
    private String contactAddress;
    /**活動報名紀錄總筆數 */
    private int totalRegistration;
    /**活動報名紀錄 */
    private List<EventRegistrationItem> registrationList;
    /**登入紀錄總筆數 */
    private int totalLogin;
    /**登入紀錄 */
    private List<AdminLoginDto> loginList;

    /**活動報名紀錄項目 */
    @Data
    public class EventRegistrationItem {
        /** 活動名稱 */
        private String eventName;
        /** 報名日期 */
        private String registrationDate;
        /** 報名狀態 */
        private String registrationStatus;
        /** 付款狀態 */
        private String paymentStatus;
        /** 攤位（若為 null 顯示 "-"） */
        private String booth; 
    }

    
}
