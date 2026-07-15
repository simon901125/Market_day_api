package com.example.demo.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 包含通知類型、標題、內容、是否已讀、已讀時間<br>
 * <b>FK</b>:接收通知的使用者{@link User}
 */
@Entity
@Data
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 接收通知的使用者 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "FK_notifications_users"))
    private User user;

    /** 通知類型 */
    @Column(name = "type", length = 50, nullable = false)
    private String type;

    /** 通知標題 */
    @Column(name = "title", length = 150, nullable = false)
    private String title;

    /** 通知內容 */
    @Column(name = "content", columnDefinition = "nvarchar(max)", nullable = false)
    private String content;

    /** 是否已讀 */
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    /** 已讀時間 */
    @Column(name = "read_at")
    private LocalDateTime readAt;
}
