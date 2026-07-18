package com.example.demo.Repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.Repository.projection.admin.AdminNoticeProjection;
import com.example.demo.entity.Notification;
import com.example.demo.enums.notification.NotificationCategory;

public interface NotificationRepo extends JpaRepository<Notification, Long> {

    /** 管理員後台: 通知中心列表 (依未讀優先、時間新到舊排序；category 為 null 時查詢全部分類；isRead 為 null 時不篩選已讀狀態) */
    @Query("""
            SELECT new com.example.demo.Repository.projection.admin.AdminNoticeProjection(
                n.id,
                n.type,
                n.targetType,
                n.targetId,
                n.title,
                n.content,
                n.isRead,
                n.createdAt
            )
            FROM Notification n
            WHERE n.user.id = :userId
              AND (:category IS NULL OR n.category = :category)
              AND (:isRead IS NULL OR n.isRead = :isRead)
            ORDER BY n.isRead ASC, n.createdAt DESC
            """)
    List<AdminNoticeProjection> findAdminNotices(
            @Param("userId") Long userId,
            @Param("category") NotificationCategory category,
            @Param("isRead") Boolean isRead,
            Pageable pageable);

    /** 計算指定管理員符合分類與已讀狀態條件的通知總筆數 (isRead 為 null 時不篩選已讀狀態) */
    @Query("""
            SELECT count(n.id)
            FROM Notification n
            WHERE n.user.id = :userId
              AND (:category IS NULL OR n.category = :category)
              AND (:isRead IS NULL OR n.isRead = :isRead)
            """)
    long countAdminNotices(
            @Param("userId") Long userId,
            @Param("category") NotificationCategory category,
            @Param("isRead") Boolean isRead);

    /** 計算指定管理員未讀且符合分類條件的通知總筆數 (管理員後台首頁: 系統警告計數) */
    @Query("""
            SELECT count(n.id)
            FROM Notification n
            WHERE n.user.id = :userId
              AND n.category = :category
              AND n.isRead = false
            """)
    long countUnreadNoticesByCategory(@Param("userId") Long userId, @Param("category") NotificationCategory category);
}
