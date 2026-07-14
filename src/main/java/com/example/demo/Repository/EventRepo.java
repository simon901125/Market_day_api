package com.example.demo.Repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.Repository.projection.admin.AdminEventDetailProjection;
import com.example.demo.Repository.projection.admin.AdminOrgEventLogProjection;
import com.example.demo.entity.MarketEvent;
import com.example.demo.enums.status.WorkflowStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepo extends JpaRepository<MarketEvent, Long>, JpaSpecificationExecutor<MarketEvent>, EventRepoCustom {
    int countByWorkflowStatus(WorkflowStatus workflowStatus);

    /** 計算(活動狀態=ACTIVE(前端:活動中))的數量 */
    @Query("select count(e.id) from MarketEvent e where e.workflowStatus = 'FINAL_REVIEW' and e.startAt <= :now and e.endAt >= :now")
    int countByEventStatusIsACTIVE(@Param("now") LocalDateTime now);

    /** 計算(活動狀態=已經在平台發布並且沒有下架也沒有結束)的數量 */
    @Query("""
        select count(e.id) 
        from MarketEvent e
        where (e.workflowStatus = 'PUBLISHED' or e.workflowStatus = 'FINAL_REVIEW') 
            and e.publicInfoAt is not null 
            and e.publicInfoAt <= :now and e.endAt >= :now
        """)
    int countByEventInPlatform(@Param("now") LocalDateTime now);

    /** 管理員後台: 主辦方詳細:活動管理紀錄列表 (只撈頁面需要用到的欄位，依活動開始時間新到舊) */
    @Query("""
            SELECT new com.example.demo.Repository.projection.admin.AdminOrgEventLogProjection(
                e.id,
                e.title,
                e.startAt,
                e.endAt,
                e.workflowStatus,
                e.registrationStartAt,
                e.registrationEndAt,
                e.brandPublicAt,
                e.maxBooths
            )
            FROM MarketEvent e
            WHERE e.user.id = :userId
            ORDER BY e.startAt DESC
            """)
    List<AdminOrgEventLogProjection> findOrgEventLogs(@Param("userId") Long userId, Pageable pageable);

    /** 計算指定主辦方的活動總筆數 */
    @Query("select count(e.id) from MarketEvent e where e.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    /** 管理員後台: 主辦方詳細:主辦方建立活動總數(不含草稿、已取消、已下架) */
    @Query("""
            select count(e.id)
            from MarketEvent e
            where e.user.id = :userId
                and e.workflowStatus not in ('CANCELLED', 'UNPUBLISHED', 'DRAFT')
            """)
    int countCreatedEventsByUserId(@Param("userId") Long userId);

    /** 管理員後台: 主辦方詳細:主辦方尚未結束的活動數(FINAL_REVIEW狀態需活動尚未結束) */
    @Query("""
            select count(e.id)
            from MarketEvent e
            where e.user.id = :userId
                and (
                    e.workflowStatus not in ('CANCELLED', 'UNPUBLISHED', 'DRAFT', 'FINAL_REVIEW')
                    or (e.workflowStatus = 'FINAL_REVIEW' and e.endAt >= :now)
                )
            """)
    int countOngoingEventsByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** 管理員後台: 主辦方詳細:主辦方已結束的活動數(FINAL_REVIEW狀態且活動已結束) */
    @Query("""
            select count(e.id)
            from MarketEvent e
            where e.user.id = :userId
                and e.workflowStatus = 'FINAL_REVIEW'
                and e.endAt <= :now
            """)
    int countEndedEventsByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** 管理員後台: 活動詳細 (不含攤位分區清單，需另外查詢) */
    @Query("""
            SELECT new com.example.demo.Repository.projection.admin.AdminEventDetailProjection(
                market.id,
                market.title,
                category.name,
                market.startAt,
                market.endAt,
                market.brandPublicAt,
                market.locationName,
                market.city,
                market.district,
                market.address,
                market.workflowStatus,
                market.coverImageUrl,
                market.description,
                market.registrationStartAt,
                market.registrationEndAt,
                market.publicInfoAt,
                market.maxBooths,
                market.baseFee,
                market.stallWidth,
                market.stallLength,
                market.mapImageUrl,
                organizerProfile.organizerName,
                userProfile.contactName,
                userProfile.contactPhone,
                userProfile.contactEmail,
                userProfile.city,
                userProfile.district,
                userProfile.address,
                organizerProfile.taxId,
                organizerProfile.serviceDays,
                organizerProfile.serviceStartTime,
                organizerProfile.serviceEndTime,
                market.metro,
                market.bus,
                market.driving
            )
            FROM MarketEvent market
            JOIN market.category category
            JOIN market.user user
            LEFT JOIN user.userProfile userProfile
            LEFT JOIN userProfile.organizerProfile organizerProfile
            WHERE market.id = :id
            """)
    Optional<AdminEventDetailProjection> findEventDetailById(@Param("id") Long id);

}
