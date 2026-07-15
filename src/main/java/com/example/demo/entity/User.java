package com.example.demo.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.type.Role;

/**
 * 使用者的Entity。<br>
 * 包含角色類型、email、密碼(加密後)、帳號登入方式、google帳號唯一識別碼、使用者帳號狀態、是否已有登入中的裝置、使用者Email驗證完成時間、使用者自動登出判斷時間、使用者帳號創建時間、使用者帳號更新時間<br>
 * 
 * @see MarketEvent#user
 * @see EventApplication#user
 * @see UserProfile#user
 * @see AdminProfile#user
 * @see Notification#user
 */
@Entity
@Data
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = " UQ_users_email", columnNames = "email"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**角色類型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 30, nullable = false)
    private Role role;

    /**email */
    @Column(name = "email", length = 255, nullable = false, unique = true)
    private String email;

    /**密碼(加密後)*/
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    /** 帳號登入方式 */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 30, nullable = false)
    private Provider provider;

    /**google帳號唯一識別碼 */
    @Column(name = "google_sub", length = 255)
    private String googleSub;

    /** 使用者帳號狀態 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private UserStatus status = UserStatus.UNACTIVE;

    /** 是否已有登入中的裝置 */
    @Column(name = "isLogin", nullable = false)
    private Boolean isLogin = false;

    /** 使用者Email驗證完成時間 */
    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    /** 使用者自動登出判斷時間 */
    @Column(name = "expired_time", nullable = false)
    private LocalDateTime expiredTime;

    /** 使用者帳號創建時間 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 使用者帳號更新時間 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    //----------我是分隔線----------
    
    /** 使用者的主辦方/攤主資料清單 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private UserProfile userProfile;

    public void setUserProfile(UserProfile userProfile) {
        this.userProfile = userProfile;
        if (userProfile != null) {
            userProfile.setUser(this);
        }
    }

    /** 使用者的管理員資料 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private AdminProfile adminProfile;

    public void setAdminProfile(AdminProfile adminProfile) {
        this.adminProfile = adminProfile;
        if (adminProfile != null) {
            adminProfile.setUser(this);
        }
    }

    /** 使用者舉辦的活動清單 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "user")
    private List<MarketEvent> marketEvents;

    /** 使用者的攤位報名清單 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "user")
    private List<EventApplication> eventApplications;

    /**管理員操作紀錄 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "user")
    private List<AdminOperationLog> adminOperationLogs;

    /**API請求紀錄 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "user")
    private List<RequestLog> requestLogs;

    /** 使用者收到的通知清單 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "user")
    private List<Notification> notifications;

    //----------我是分隔線----------

    /** 帳號登入方式 */
    public enum Provider {
        GOOGLE,
        BOTH,
        LOCAL
    }
}
