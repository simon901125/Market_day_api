package com.example.demo.Repository.specification;

import java.time.LocalDateTime;

import org.springframework.data.jpa.domain.Specification;

import com.example.demo.dto.request.admin.AdminLogSearchDto;
import com.example.demo.entity.AdminOperationLog;
import com.example.demo.entity.MarketEvent;
import com.example.demo.entity.User;
import com.example.demo.enums.type.AdminOperationType;
import com.example.demo.enums.type.AdminTargetType;
import com.example.demo.enums.type.AdminTargetTypeForFront;
import com.example.demo.enums.type.Role;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * 
 * 管理員: Log搜尋頁面 動態查詢條件組合
 */
public class AdminLogSpecification {

    /** 依AdminLogSearchDto組合出完整查詢條件 */
    public static Specification<AdminOperationLog> build(AdminLogSearchDto request) {
        return Specification.allOf(
                withKeyword(request.keyWord()),
                withOperationType(request.operationType()),
                withTargetType(request.targetType()),
                withStartAt(request.startAt()),
                withEndAt(request.endAt()));
    }

    /*
     * 操作人 -> 待確定
     * 操作對象 -> where adminOperationLog.targetLabel like %keyword%
     * 操作對象email -> 用email查到user.id，然後用user.id查user.role，
     * if user.role = vender 用 where user.id = adminOperationLog.targetId and
     * targetType = user;
     * if user.role = organizer, 用 user.id 查 market.id, (where user.id =
     * adminOperationLog.targetId and targetType = user) or (where market.id =
     * adminOperationLog.targetId and (targetType = marketEvent or targetType =
     * eventUnpublishRequest))
     * if user.role = admin, return null;
     * 操作內容 -> where adminOperationLog.content like %keyword%
     */
    /** 操作人/操作對象/操作對象email/操作內容模糊搜尋 */
    private static Specification<AdminOperationLog> withKeyword(String keyword) {
        return (root, cq, cb) -> {
            if (keyword == null) {
                return null;
            }
            return cb.or(
                    cb.like(cb.lower(root.get("targetLabel")), "%" + keyword.toLowerCase() + "%"),
                    cb.like(cb.lower(root.get("content")), "%" + keyword.toLowerCase() + "%"),
                    withTargetEmail(root, cq, cb, keyword));

        };
    }

    /** 操作類型搜尋 */
    private static Specification<AdminOperationLog> withOperationType(AdminOperationType operationType) {
        return (root, cq, cb) -> {
            if (operationType == null) {
                return null;
            } 
            
            return cb.equal(root.get("operationType"), operationType);
        };
    }

    /**
     * 操作對象類型搜尋。<br>
     * 對應邏輯與{@link #targetTypeForFrontExpression}相反：
     * SYSTEM_SETTING/MARKET_EVENT直接對應targetType；
     * ORGANIZER/VENDOR則需先查出該role的user.id，再比對targetType=USER且targetId符合。
     */
    private static Specification<AdminOperationLog> withTargetType(AdminTargetTypeForFront targetType) {
        return (root, cq, cb) -> {
            if (targetType == null) {
                return null;
            }
            switch (targetType) {
                case SYSTEM_SETTING:
                    return cb.equal(root.get("targetType"), AdminTargetType.SYSTEM_SETTING);
                case MARKET_EVENT:
                    return root.get("targetType").in(
                            AdminTargetType.MARKET_EVENT, AdminTargetType.EVENT_UNPUBLISH_REQUEST);
                case ORGANIZER:
                    return cb.and(
                            cb.equal(root.get("targetType"), AdminTargetType.USER),
                            root.get("targetId").in(userIdSubqueryByRole(cq, cb, Role.ORGANIZER)));
                case VENDOR:
                    return cb.and(
                            cb.equal(root.get("targetType"), AdminTargetType.USER),
                            root.get("targetId").in(userIdSubqueryByRole(cq, cb, Role.VENDOR)));
                default:
                    return null;
            }
        };
    }

    /** 操作時間開始時間搜尋(在此時間之後開始) */
    private static Specification<AdminOperationLog> withStartAt(LocalDateTime startAt) {
        return (root, query, cb) -> {
            if (startAt == null) {
                return null;
            }
            return cb.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), startAt);
        };
    }

    /** 操作時間時間搜尋(在此時間之前結束) */
    private static Specification<AdminOperationLog> withEndAt(LocalDateTime endAt) {
        return (root, cq, cb) -> {
            if (endAt == null) {
                return null;
            }
            return cb.lessThan(root.<LocalDateTime>get("createdAt"), endAt.plusDays(1));
        };
    }

    /**
     * 依操作對象email搜尋。<br>
     * 用email模糊比對出的user，若非admin視為targetType=USER的操作對象；<br>
     * 若該user是organizer，其名下活動的market.id視為targetType=MARKET_EVENT或EVENT_UNPUBLISH_REQUEST的操作對象。<br>
     * role篩選直接寫進subquery條件，讓admin/vendor自然查不到不該匹配的資料，不需另外用if/else分支處理。
     */
    private static Predicate withTargetEmail(Root<AdminOperationLog> root, CriteriaQuery<?> cq, CriteriaBuilder cb,
            String keyword) {
        String pattern = "%" + keyword.toLowerCase() + "%";

        Subquery<Long> userIdSubquery = cq.subquery(Long.class);
        Root<User> userRoot = userIdSubquery.from(User.class);
        userIdSubquery.select(userRoot.get("id"))
                .where(cb.like(cb.lower(userRoot.get("email")), pattern),
                        cb.notEqual(userRoot.get("role"), Role.ADMIN));

        Subquery<Long> marketIdSubquery = cq.subquery(Long.class);
        Root<MarketEvent> marketRoot = marketIdSubquery.from(MarketEvent.class);
        marketIdSubquery.select(marketRoot.get("id"))
                .where(cb.like(cb.lower(marketRoot.get("user").get("email")), pattern),
                        cb.equal(marketRoot.get("user").get("role"), Role.ORGANIZER));

        return cb.or(
                cb.and(cb.equal(root.get("targetType"), AdminTargetType.USER), root.get("targetId").in(userIdSubquery)),
                cb.and(root.get("targetType").in(AdminTargetType.MARKET_EVENT, AdminTargetType.EVENT_UNPUBLISH_REQUEST),
                        root.get("targetId").in(marketIdSubquery)));
    }

    /** 依role查出對應user.id的subquery */
    private static Subquery<Long> userIdSubqueryByRole(CriteriaQuery<?> cq, CriteriaBuilder cb, Role role) {
        Subquery<Long> userIdSubquery = cq.subquery(Long.class);
        Root<User> userRoot = userIdSubquery.from(User.class);
        userIdSubquery.select(userRoot.get("id"))
                .where(cb.equal(userRoot.get("role"), role));
        return userIdSubquery;
    }
}
