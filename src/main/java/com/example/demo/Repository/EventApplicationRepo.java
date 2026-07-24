package com.example.demo.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.Repository.projection.admin.ApplicationDateProjection;
import com.example.demo.Repository.projection.admin.RefundProjection;
import com.example.demo.Repository.projection.admin.VenderRegApplicationProjection;
import com.example.demo.entity.EventApplication;

public interface EventApplicationRepo extends JpaRepository<EventApplication, Long> {

    /** 管理員後台: 攤主詳細:活動報名紀錄列表 (依報名建立時間新到舊) */
    @Query("""
            SELECT new com.example.demo.Repository.projection.admin.VenderRegApplicationProjection(
                a.id,
                e.id,
                e.title,
                a.reviewStatus,
                a.paymentStatus,
                a.isCancelled
            )
            FROM EventApplication a
            JOIN a.event e
            WHERE a.user.id = :userId and e.workflowStatus not in ('DRAFT', 'CANCELLED')
            ORDER BY a.createdAt DESC
            """)
    List<VenderRegApplicationProjection> findVenderRegApplications(@Param("userId") Long userId, Pageable pageable);

    /** 計算指定使用者的報名紀錄總筆數 */
    @Query("select count(a.id) from EventApplication a where a.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    /** 計算活動目前報名攤位(不計入被拒絕的攤位) */
    @Query("""
        select count(a.id)
        from EventApplication a
        where a.event.id = :eventId
            and a.reviewStatus != 'REJECTED'
        """)
    int countRegBoothsByEventId(@Param("eventId") Long eventId);

    /** 依報名編號清單查詢報名的參與日期與已選定攤位 */
    @Query("""
            SELECT new com.example.demo.Repository.projection.admin.ApplicationDateProjection(
                d.application.id,
                d.applyDate,
                s.stallNo,
                z.zoneName
            )
            FROM ApplicationDate d
            LEFT JOIN d.selectedStall s
            LEFT JOIN s.zone z
            WHERE d.application.id IN :applicationIds
            ORDER BY d.applyDate ASC
            """)
    List<ApplicationDateProjection> findApplicationDates(@Param("applicationIds") List<Long> applicationIds);

    /** 依報名編號清單查詢退款紀錄的退款完成時間 */
    @Query("""
            SELECT new com.example.demo.Repository.projection.admin.RefundProjection(
                r.application.id,
                r.refundedAt
            )
            FROM Refund r
            WHERE r.application.id IN :applicationIds
            """)
    List<RefundProjection> findRefunds(@Param("applicationIds") List<Long> applicationIds);

    /** 管理員後台: 攤主詳細:攤主報名且尚未結束的活動數量 */
    @Query("""
            select count(a.id)
            from EventApplication a
            join a.event e
            where a.user.id = :userId
                and a.isCancelled = false
                and a.reviewStatus <> 'REJECTED'
                and a.paymentStatus <> 'EXPIRED'
                and e.endAt > :now
                and e.workflowStatus in ('PUBLISHED', 'FINAL_REVIEW', 'UNPUBLISH_REQUESTED')
            """)
    int countOngoingEvents(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** 管理員後台: 攤主詳細:攤主已完成(已結束且已付款)的活動數量 */
    @Query("""
            select count(a.id)
            from EventApplication a
            join a.event e
            where a.user.id = :userId
                and a.isCancelled = false
                and a.reviewStatus = 'APPROVED'
                and a.paymentStatus = 'PAID'
                and e.endAt <= :now
                and e.workflowStatus = 'FINAL_REVIEW'
            """)
    int countEndedEvents(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** 管理員後台: 檢查活動是否還有已付款且未取消的報名申請(款項尚未處理) */
    @Query("""
            select count(a.id) > 0
            from EventApplication a
            where a.event.id = :eventId
                and a.isCancelled = false
                and a.paymentStatus = 'PAID'
            """)
    boolean existsUnprocessedPayment(@Param("eventId") Long eventId);
}
