package com.example.demo.Repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.Repository.projection.admin.EventStatusLogProjection;
import com.example.demo.entity.StatusLog;

public interface StatusLogRepo extends JpaRepository<StatusLog, Long> {

    /** 管理員後台: 活動詳細:活動狀態變動紀錄列表 (依請求時間新到舊) */
    @Query("""
            SELECT new com.example.demo.Repository.projection.admin.EventStatusLogProjection(
                r.createdAt,
                s.newStatus,
                u.role,
                p.contactName,
                a.name
            )
            FROM StatusLog s
            JOIN s.requestLog r
            JOIN r.user u
            LEFT JOIN u.userProfile p
            LEFT JOIN u.adminProfile a
            WHERE s.targetType = 'EVENT' AND s.targetId = :eventId AND s.statusField in ('workflow_status', 'payment_received') 
            ORDER BY r.createdAt DESC
            """)
    List<EventStatusLogProjection> findEventStatusLogs(@Param("eventId") Long eventId, Pageable pageable);

    /** 計算指定活動的狀態變動紀錄總筆數 */
    @Query("""
            select count(s.id)
            from StatusLog s
            where s.targetType = 'EVENT' and s.targetId = :eventId and s.statusField = 'workflow_status'
            """)
    long countEventStatusLogs(@Param("eventId") Long eventId);
}
