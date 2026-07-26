package com.example.demo.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.example.demo.Repository.support.AbstractTupleQuerySupport;
import com.example.demo.entity.RequestLog;
import com.example.demo.entity.User;
import com.example.demo.entity.UserProfile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * 
 * 使用者id、角色、名稱、帳號狀態、email、註冊時間、最後登入時間
 * @see com.example.demo.dto.response.admin.AdminUserListDto
 */
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
        Join<User, UserProfile> userProfile = root.join("userProfile", JoinType.LEFT);

        // 設定搜尋條件
        applyPredicate(root, cq, cb, spec);
        //設定子查詢
        Subquery<LocalDateTime> loginTimeSubquery = buildLoginTimeSubquery(cb, cq, root);

        // 組裝select欄位
        cq.multiselect(
                root.get("id").alias("id"),//使用者id
                root.get("role").alias("role"),//角色
                userProfile.get("contactName").alias("name"),//名稱
                root.get("status").alias("status"),//帳號狀態
                root.get("email").alias("email"),//email
                root.get("createdAt").alias("regAt"),//註冊時間
                loginTimeSubquery.alias("loginTime"));//最後登入時間

        // 設定orderBy: 帳號創建時間:由新到舊(desc)
        cq.orderBy(
                cb.desc(root.get("createdAt")),
                cb.desc(root.get("id")));

        // 查詢結果(有設定limit)
        return fetchPage(cq, pageNumber, pageSize);
    }

    /** 建立最後登入時間子查詢: 取狀態碼200的登入類API請求中，最新的請求時間 */
    private static Subquery<LocalDateTime> buildLoginTimeSubquery(CriteriaBuilder cb, CriteriaQuery<?> cq,
            Root<User> root) {

        List<String> loginPaths = List.of(
                "/api/admin/local-login",
                "/api/organizer/google-login",
                "/api/organizer/local-login",
                "/api/vendor/google-login",
                "/api/vendor/local-login");

        Subquery<LocalDateTime> loginTimeSubquery = cq.subquery(LocalDateTime.class);
        Root<RequestLog> requestLog = loginTimeSubquery.from(RequestLog.class);
        Root<User> correlatedUser = loginTimeSubquery.correlate(root);

        loginTimeSubquery.select(cb.greatest(requestLog.<LocalDateTime>get("createdAt")));
        loginTimeSubquery.where(
                cb.equal(requestLog.get("user"), correlatedUser),
                cb.equal(requestLog.get("statusCode"), 200),
                requestLog.get("path").in(loginPaths));

        return loginTimeSubquery;
    }
}
