package com.example.demo.Repository;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.example.demo.Repository.support.AbstractTupleQuerySupport;
import com.example.demo.entity.User;
import com.example.demo.entity.UserProfile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;

public class UserRepoCustomImpl extends AbstractTupleQuerySupport implements UserRepoCustom {

    public UserRepoCustomImpl(EntityManager entityManager) {
        super(entityManager);
    }

    @Override
    public List<Tuple> findUserListTuples(Specification<User> spec, int pageNumber, int pageSize) {
        // 設定要join的表
        TupleQueryContext<User> ctx = newTupleQuery(User.class);
        CriteriaBuilder cb = ctx.cb();
        CriteriaQuery<Tuple> cq = ctx.cq();
        Root<User> root = ctx.root();
        Join<User, UserProfile> userProfile = root.join("userProfile");

        // 設定搜尋條件
        applyPredicate(root, cq, cb, spec);

        // 組裝select欄位
        cq.multiselect(
                root.get("id").alias("id"),
                root.get("role").alias("role"),
                userProfile.get("name").alias("name"),
                root.get("status").alias("status"),
                root.get("email").alias("email"),
                root.get("createdAt").alias("regAt"));
        // 設定orderBy: 帳號創建時間:由新到舊(desc)
        cq.orderBy(cb.desc(root.get("createdAt")));

        // 查詢結果(有設定limit)
        return fetchPage(cq, pageNumber, pageSize);

        //查user最後登入時間
        /*
        select 
        from requestLog l
        where statusCode = 200 and path like %login 
        */

    }
}
