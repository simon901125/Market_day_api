package com.example.demo.Repository.specification;

import java.time.LocalDateTime;

import org.springframework.data.jpa.domain.Specification;

import com.example.demo.dto.request.admin.AdminEventSearchDto;
import com.example.demo.entity.EventApplication;
import com.example.demo.entity.MarketEvent;
import com.example.demo.enums.EventStatus;
import com.example.demo.enums.ReviewStatus;
import com.example.demo.enums.WorkflowStatus;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** 管理員: 活動搜尋頁面 動態查詢條件組合 */
public class EventSpecification {

    /** 依AdminEventSearchDto組合出完整查詢條件 */
    public static Specification<MarketEvent> build(AdminEventSearchDto request) {
        return Specification.allOf(
                withKeywordName(request.keywordName()),
                withOrganizer(request.organizer()),
                withStartAt(request.startAt()),
                withEndAt(request.endAt()),
                withStatus(request.status()));
    }

    /** 活動名稱/主辦方名稱模糊搜尋 */
    private static Specification<MarketEvent> withKeywordName(String keywordName) {
        return (root, query, cb) -> {
            if (keywordName == null || keywordName.isBlank()) {
                return null;
            }
            return cb.or(
                cb.like(cb.lower(root.get("title")), "%" + keywordName.toLowerCase() + "%"), 
                cb.like(cb.lower(root.get("user").get("userProfile").get("name")), "%" + keywordName.toLowerCase() + "%"));
        };
    }

    /** 主辦方名稱搜尋(join user.userProfile.name) */
    private static Specification<MarketEvent> withOrganizer(String organizer) {
        return (root, query, cb) -> {
            if (organizer == null || organizer.isBlank()) {
                return null;
            }
            return cb.equal(
                    cb.lower(root.get("user").get("userProfile").get("name")), organizer.toLowerCase());
        };
    }

    /** 活動開始時間搜尋(在此時間之後開始) */
    private static Specification<MarketEvent> withStartAt(LocalDateTime startAt) {
        return (root, query, cb) -> {
            if (startAt == null) {
                return null;
            }
            return cb.greaterThanOrEqualTo(root.<LocalDateTime>get("startAt"), startAt);
        };
    }

    /** 活動結束時間搜尋(在此時間之前結束) */
    private static Specification<MarketEvent> withEndAt(LocalDateTime endAt) {
        return (root, query, cb) -> {
            if (endAt == null) {
                return null;
            }
            return cb.lessThan(root.<LocalDateTime>get("endAt"), endAt.plusDays(1));
        };
    }

    /**
     * 活動狀態搜尋
     * 注意: EventStatus是由workflowStatus + 多個時間欄位 + 報名人數即時運算出來的，
     * 對應邏輯須與{@link com.example.demo.Service.EventStatusServiceInterface#checkEventStatus}保持一致，兩邊修改時要一起改
     */
    private static Specification<MarketEvent> withStatus(EventStatus status) {
        return (root, query, cb) -> {
            if (status == null) {
                return null;
            }
            LocalDateTime now = LocalDateTime.now();
            Expression<Long> registeredBoothCount = registeredBoothCountSubquery(root, query, cb);

            return switch (status) {
                case DRAFT -> cb.equal(root.get("workflowStatus"), WorkflowStatus.DRAFT);
                case PENDING_REVIEW -> cb.equal(root.get("workflowStatus"), WorkflowStatus.PENDING_REVIEW);
                case REVISION_REQUIRED -> cb.equal(root.get("workflowStatus"), WorkflowStatus.REVISION_REQUIRED);
                case MAP_BUILDING -> cb.equal(root.get("workflowStatus"), WorkflowStatus.MAP_BUILDING);
                case READY_TO_PUBLISH -> cb.or(
                        cb.equal(root.get("workflowStatus"), WorkflowStatus.READY_TO_PUBLISH),
                        cb.and(
                                cb.equal(root.get("workflowStatus"), WorkflowStatus.PUBLISHED),
                                cb.greaterThan(root.<LocalDateTime>get("registrationStartAt"), now)));
                case REGISTRATION_OPEN -> cb.and(
                        cb.equal(root.get("workflowStatus"), WorkflowStatus.PUBLISHED),
                        cb.lessThan(root.<LocalDateTime>get("registrationStartAt"), now),
                        cb.greaterThan(root.<LocalDateTime>get("registrationEndAt"), now),
                        cb.le(registeredBoothCount, root.<Integer>get("maxBooths")));
                case FULL -> cb.or(
                        cb.and(
                                cb.equal(root.get("workflowStatus"), WorkflowStatus.PUBLISHED),
                                cb.lessThan(root.<LocalDateTime>get("registrationStartAt"), now),
                                cb.not(cb.and(
                                        cb.greaterThan(root.<LocalDateTime>get("registrationEndAt"), now),
                                        cb.le(registeredBoothCount, root.<Integer>get("maxBooths"))))),
                        cb.and(
                                cb.equal(root.get("workflowStatus"), WorkflowStatus.FINAL_REVIEW),
                                cb.greaterThan(root.<LocalDateTime>get("brandPublicAt"), now)));
                case PUBLISHED -> cb.and(
                        cb.equal(root.get("workflowStatus"), WorkflowStatus.FINAL_REVIEW),
                        cb.lessThanOrEqualTo(root.<LocalDateTime>get("brandPublicAt"), now),
                        cb.greaterThan(root.<LocalDateTime>get("startAt"), now));
                case ACTIVE -> cb.and(
                        cb.equal(root.get("workflowStatus"), WorkflowStatus.FINAL_REVIEW),
                        cb.lessThanOrEqualTo(root.<LocalDateTime>get("startAt"), now),
                        cb.greaterThan(root.<LocalDateTime>get("endAt"), now));
                case ENDED -> cb.and(
                        cb.equal(root.get("workflowStatus"), WorkflowStatus.FINAL_REVIEW),
                        cb.lessThanOrEqualTo(root.<LocalDateTime>get("brandPublicAt"), now),
                        cb.lessThanOrEqualTo(root.<LocalDateTime>get("startAt"), now),
                        cb.lessThanOrEqualTo(root.<LocalDateTime>get("endAt"), now));
                case UNPUBLISH_REQUESTED -> cb.equal(root.get("workflowStatus"), WorkflowStatus.UNPUBLISH_REQUESTED);
                case UNPUBLISHED -> cb.equal(root.get("workflowStatus"), WorkflowStatus.UNPUBLISHED);
            };
        };
    }

    /**
     * 計算活動目前報名攤位數(不計入被拒絕的攤位)，作法對應{@link com.example.demo.Repository.EventRepo#countRegBoothsByEventId}
     */
    public static Expression<Long> registeredBoothCountSubquery(
            Root<MarketEvent> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<EventApplication> application = subquery.from(EventApplication.class);
        Root<MarketEvent> correlatedEvent = subquery.correlate(root);

        subquery.select(cb.count(application.get("id")));
        subquery.where(
                cb.equal(application.get("event"), correlatedEvent),
                cb.notEqual(application.get("reviewStatus"), ReviewStatus.REJECTED));

        return subquery;
    }
}
