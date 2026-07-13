package com.example.demo.Repository;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.example.demo.Repository.specification.EventSpecification;
import com.example.demo.Repository.support.AbstractTupleQuerySupport;
import com.example.demo.entity.MarketEvent;
import com.example.demo.entity.User;
import com.example.demo.entity.UserProfile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;

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
                root.get("createAt").alias("createAt"),
                root.get("workflowStatus").alias("workflowStatus"),
                root.get("registrationStartAt").alias("registrationStartAt"),
                root.get("registrationEndAt").alias("registrationEndAt"),
                root.get("brandPublicAt").alias("brandPublicAt"),
                root.get("maxBooths").alias("maxBooths"),
                EventSpecification.registeredBoothCountSubquery(root, cq, cb).alias("registeredBoothCount"));
        // 設定orderBy: 活動創建時間:由新到舊(desc)
        cq.orderBy(cb.desc(root.get("createAt")));

        // 查詢結果(有設定limit)
        return fetchPage(cq, pageNumber, pageSize);
    }
}
