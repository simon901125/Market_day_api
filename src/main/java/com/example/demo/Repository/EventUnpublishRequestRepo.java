package com.example.demo.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.Repository.projection.admin.EventUnpublishReasonProjection;
import com.example.demo.Repository.projection.admin.EventUnpublishReviewProjection;
import com.example.demo.entity.EventUnpublishRequest;
import com.example.demo.entity.User;
import com.example.demo.enums.status.UnpublishRequestStatus;

public interface EventUnpublishRequestRepo extends JpaRepository<EventUnpublishRequest, Long> {

    /** 管理員後台: 下架申請退回:查詢指定下架申請單的狀態，及其對應活動的流程狀態、名稱、品牌公開時間與擁有者id */
    @Query("""
            SELECT new com.example.demo.Repository.projection.admin.EventUnpublishReviewProjection(
                r.status,
                e.id,
                e.workflowStatus,
                e.title,
                e.brandPublicAt,
                e.user.id
            )
            FROM EventUnpublishRequest r
            JOIN r.event e
            WHERE r.id = :unpublishRequestId
            """)
    Optional<EventUnpublishReviewProjection> findReviewInfoById(
            @Param("unpublishRequestId") Long unpublishRequestId);

    /** 管理員後台: 活動詳細:查詢指定活動、指定狀態中，申請時間最新的一筆下架申請id與原因 */
    @Query("""
            SELECT new com.example.demo.Repository.projection.admin.EventUnpublishReasonProjection(
                r.id,
                r.reason
            )
            FROM EventUnpublishRequest r
            WHERE r.event.id = :eventId
                AND r.status = :status
                AND r.requestedAt = (
                    SELECT MAX(r2.requestedAt)
                    FROM EventUnpublishRequest r2
                    WHERE r2.event.id = :eventId AND r2.status = :status
                )
            """)
    Optional<EventUnpublishReasonProjection> findLatestReasonByEventIdAndStatus(
            @Param("eventId") Long eventId, @Param("status") UnpublishRequestStatus status);

    /** 管理員後台: 確認活動下架:查詢指定活動、指定狀態中，申請時間最新的一筆下架申請id */
    @Query("""
            SELECT r.id
            FROM EventUnpublishRequest r
            WHERE r.event.id = :eventId
                AND r.status = :status
                AND r.requestedAt = (
                    SELECT MAX(r2.requestedAt)
                    FROM EventUnpublishRequest r2
                    WHERE r2.event.id = :eventId AND r2.status = :status
                )
            """)
    Optional<Long> findLatestRequestIdByEventIdAndStatus(
            @Param("eventId") Long eventId, @Param("status") UnpublishRequestStatus status);

    /** 管理員後台: 確認活動下架:僅當目前狀態符合預期時才更新審核人員、狀態、審核備註與審核時間，回傳影響筆數 */
    @Modifying
    @Query("""
            UPDATE EventUnpublishRequest r
            SET r.reviewUser = :reviewer, r.status = :newStatus, r.reviewNote = :reviewNote, r.reviewedAt = CURRENT_TIMESTAMP
            WHERE r.id = :requestId AND r.status = :expectedStatus
            """)
    int reviewIfCurrent(
            @Param("requestId") Long requestId,
            @Param("reviewer") User reviewer,
            @Param("expectedStatus") UnpublishRequestStatus expectedStatus,
            @Param("newStatus") UnpublishRequestStatus newStatus,
            @Param("reviewNote") String reviewNote);
}
