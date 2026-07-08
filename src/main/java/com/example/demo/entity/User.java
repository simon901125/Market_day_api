package com.example.demo.entity;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;

import com.example.demo.enums.Role;

/**
 * User
 * 使用者的Entity
 */
@Entity
@Data
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 30, nullable = false)
    private Role role;

    @Column(name = "email", length = 255, nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "phone", length = 30)
    private String phone;

    /**帳號登入方式 */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 30, nullable = false)
    private Provider provider;

    @Column(name = "google_sub", length = 255)
    private String googleSub;

    /**使用者帳號狀態 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private Status status = Status.UNACTIVE;

    /**是否已有登入中的裝置 */
    @Column(name = "isLogin", nullable = false)
    private Boolean isLogin = false;

    /**使用者Email驗證完成時間 */
    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    /**使用者自動登出判斷時間*/
    @Column(name = "expired_time", nullable = false)
    private LocalDateTime expiredTime;

    /**使用者帳號創建時間 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**使用者帳號更新時間 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**使用者舉辦的活動清單 */
    @OneToMany(mappedBy = "user")
    private List<MarketEvent> marketEvents;

    /**使用者的攤位報名清單*/
    @OneToMany(mappedBy = "user")
    private List<EventApplication> eventApplications;

    /**使用者的主辦方/攤主資料清單 */
    @OneToOne(mappedBy = "user")
    private UserProfile userProfile;

    public void setUserProfile(UserProfile userProfile){
        this.userProfile = userProfile;
        if (userProfile != null) {
            userProfile.setUser(this);
        }
    }

    /**帳號登入方式 */
    public enum Provider {
        GOOGLE,
        BOTH,
        LOCAL
    }

    /**使用者帳號狀態 */
    public enum Status {
        /**已刪除 */
        IS_DELETED,
        /**已停用 */
        DISABLED,
        /**活動中 */
        ACTIVE,
        /**未激活 */
        UNACTIVE
    }
}
