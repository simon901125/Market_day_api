package com.example.demo.Repository;

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
                e.title,
                a.reviewStatus,
                a.paymentStatus,
                a.isCancelled
            )
            FROM EventApplication a
            JOIN a.event e
            WHERE a.user.id = :userId
            ORDER BY a.createdAt DESC
            """)
    List<VenderRegApplicationProjection> findVenderRegApplications(@Param("userId") Long userId, Pageable pageable);

    /** 計算指定使用者的報名紀錄總筆數 */
    @Query("select count(a.id) from EventApplication a where a.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

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
}
