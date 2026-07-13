package com.example.demo.Repository.support;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/** 封裝Criteria Tuple動態查詢共用邏輯，供各Repo的Custom實作(XxxRepoImpl)繼承 */
public abstract class AbstractTupleQuerySupport {

    protected final EntityManager entityManager;

    protected AbstractTupleQuerySupport(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** 封裝一次tuple查詢共用的cb/cq/root，避免每個方法重複建立 */
    protected record TupleQueryContext<T>(CriteriaBuilder cb, CriteriaQuery<Tuple> cq, Root<T> root) {
    }

    /** 建立指定entity的tuple查詢context(cb、cq、root) */
    protected <T> TupleQueryContext<T> newTupleQuery(Class<T> entityClass) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<T> root = cq.from(entityClass);
        return new TupleQueryContext<>(cb, cq, root);
    }

    /** 套用Specification產生的搜尋條件(若有) */
    protected <T> void applyPredicate(Root<T> root, CriteriaQuery<?> cq, CriteriaBuilder cb, Specification<T> spec) {
        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
    }

    /** 依分頁參數查詢tuple結果 */
    protected List<Tuple> fetchPage(CriteriaQuery<Tuple> cq, int pageNumber, int pageSize) {
        return entityManager.createQuery(cq)
                .setFirstResult((pageNumber - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }
}
