package com.example.demo.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.example.demo.Repository.specification.EventSpecification;
import com.example.demo.Repository.support.AbstractTupleQuerySupport;
import com.example.demo.entity.MarketEvent;
import com.example.demo.entity.RequestLog;
import com.example.demo.entity.User;
import com.example.demo.entity.UserProfile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public class EventRepoCustomImpl extends AbstractTupleQuerySupport implements EventRepoCustom {

    public EventRepoCustomImpl(EntityManager entityManager) {
        super(entityManager);
    }

    @Override
    public List<Tuple> findEventListTuples(Specification<MarketEvent> spec, int pageNumber, int pageSize) {
        // 設定要join的表
        TupleQueryContext<MarketEvent> ctx = newTupleQuery(MarketEvent.class);
        CriteriaBuilder cb = ctx.cb();
        CriteriaQuery<Tuple> cq = ctx.cq();
        Root<MarketEvent> root = ctx.root();
        Join<MarketEvent, User> user = root.join("user");
        Join<User, UserProfile> userProfile = user.join("userProfile");

        // 設定搜尋條件
        applyPredicate(root, cq, cb, spec);

        // 組裝select欄位
        cq.multiselect(
                root.get("id").alias("id"),
                root.get("coverImageUrl").alias("coverImageUrl"),
                root.get("title").alias("title"),
                userProfile.get("name").alias("organizerName"),
                root.get("startAt").alias("startAt"),
                root.get("endAt").alias("endAt"),
                root.get("workflowStatus").alias("workflowStatus"),
                root.get("registrationStartAt").alias("registrationStartAt"),
                root.get("registrationEndAt").alias("registrationEndAt"),
                root.get("brandPublicAt").alias("brandPublicAt"),
                root.get("maxBooths").alias("maxBooths"),
                EventSpecification.registeredBoothCountSubquery(root, cq, cb).alias("registeredBoothCount"),
                submittedAtSubquery(root, cq, cb).alias("submittedAt")
            );
        // 設定orderBy: 活動創建時間:由新到舊(desc)
        cq.orderBy(cb.desc(root.get("createAt")));

        // 查詢結果(有設定limit)
        return fetchPage(cq, pageNumber, pageSize);
    }

    /** 查詢活動的送審時間(活動建立者第一次成功呼叫 POST /api/organizer 的時間) */
    private static Expression<LocalDateTime> submittedAtSubquery(
            Root<MarketEvent> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<LocalDateTime> subquery = query.subquery(LocalDateTime.class);
        Root<RequestLog> requestLog = subquery.from(RequestLog.class);
        Root<MarketEvent> correlatedEvent = subquery.correlate(root);

        Expression<String> submittedPath = cb.concat(
                cb.concat("/api/organizer/events/", correlatedEvent.get("id").as(String.class)),
                "/submit-review");
        subquery.select(cb.greatest(requestLog.<LocalDateTime>get("createdAt")));
        subquery.where(
                cb.equal(requestLog.get("user"), correlatedEvent.get("user")),
                cb.equal(requestLog.get("statusCode"), 200),
                cb.equal(requestLog.get("path"), submittedPath));

        return subquery;
    }
}
