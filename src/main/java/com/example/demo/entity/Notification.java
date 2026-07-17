package com.example.demo.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.example.demo.enums.type.NotificationCategory;
import com.example.demo.enums.type.NotificationTargetType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 系統通知的Entity。<br>
 * 包含通知中心分類、通知事件類型、通知關聯對象類型、通知關聯資料ID、標題、內容、是否已讀、已讀時間、建立時間<br>
 * <b>FK</b>:接收通知的使用者{@link User}
 */
@Entity
@Data
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 接收通知的使用者*/
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "FK_notifications_users"))
    private User user;

    /** 通知中心分類（攤主、主辦方及管理員共用）(對應前端的書籤) */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30, nullable = false)
    private NotificationCategory category;

    /** 通知事件類型，例如 APPLICATION_APPROVED/PAYMENT_PAID/EVENT_UPDATED (對應API實際操作行為分類)*/
    @Column(name = "type", length = 50, nullable = false)
    private String type;

    /** 通知關聯對象類型(對應API的操作對象類型) */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 30, nullable = false)
    private NotificationTargetType targetType;

    /** 通知關聯資料 ID；SYSTEM 類型為 NULL(對應API的操作對象id) */
    @Column(name = "target_id")
    private Long targetId;

    /** 通知標題 */
    @Column(name = "title", length = 150, nullable = false)
    private String title;

    /** 通知內容 */
    @Column(name = "content", columnDefinition = "nvarchar(max)", nullable = false)
    private String content;

    /** 是否已讀 */
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    /** 閱讀時間 */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /** 通知建立時間 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
