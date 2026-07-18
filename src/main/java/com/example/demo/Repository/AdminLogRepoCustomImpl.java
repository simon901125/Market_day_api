package com.example.demo.Repository;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.example.demo.Repository.specification.AdminLogSpecification;
import com.example.demo.Repository.support.AbstractTupleQuerySupport;
import com.example.demo.entity.AdminOperationLog;
import com.example.demo.entity.AdminProfile;
import com.example.demo.entity.MarketEvent;
import com.example.demo.entity.User;
import com.example.demo.enums.type.AdminTargetType;
import com.example.demo.enums.type.Role;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public class AdminLogRepoCustomImpl extends AbstractTupleQuerySupport implements AdminLogRepoCustom {

    public AdminLogRepoCustomImpl(EntityManager entityManager) {
        super(entityManager);
    }

    @Override
    public List<Tuple> findLogListTuples(Specification<AdminOperationLog> spec, int pageNumber, int pageSize) {
        // 設定要join的表
        TupleQueryContext<AdminOperationLog> ctx = newTupleQuery(AdminOperationLog.class);
        CriteriaBuilder cb = ctx.cb();
        CriteriaQuery<Tuple> cq = ctx.cq();
        Root<AdminOperationLog> root = ctx.root();
        Join<AdminOperationLog, User> user = root.join("user");
        Join<User, AdminProfile> adminProfile = user.join("adminProfile");

        // 設定搜尋條件
        applyPredicate(root, cq, cb, spec);
        Expression<String> targetEmail = targetEmailSubquery(root, cq, cb);
        Expression<Role> targetUserRole = targetUserRoleSubquery(root, cq, cb);

        // 組裝select欄位
        // 注意: AdminTargetTypeForFront沒有對應任何資料庫欄位，Hibernate 6無法在CriteriaBuilder裡
        // 把它當成case-when的字面值/參數綁定(會丟JpaSystemException: Could not determine ValueMapping)，
        // 因此這裡只SELECT有實際欄位對應的targetType(AdminTargetType)、targetUserRole(Role)兩個原始值，
        // 換算成AdminTargetTypeForFront的邏輯改放到AdminService.getLogs()的一般Java程式碼裡做。
        cq.multiselect(
                root.get("id").alias("id"),
                adminProfile.get("name").alias("adminName"),
                root.get("operationType").alias("operationType"),
                root.get("targetType").alias("targetType"),
                targetUserRole.alias("targetUserRole"),
                root.get("targetLabel").alias("targetName"),
                root.get("createdAt").alias("createdAt"),
                root.get("content").alias("content"),
                targetEmail.alias("email"));
        // 設定orderBy: 操作時間:由新到舊(desc)
        cq.orderBy(cb.desc(root.get("createdAt")));

        // 查詢結果(有設定limit)
        return fetchPage(cq, pageNumber, pageSize);
    }

    /**
     * 依targetType查出操作對象email。<br>
     * targetId沒有對應的JPA關聯(僅是純Long欄位)，無法直接join，
     * 因此依targetType用subquery查出對應email，並用selectCase依targetType挑選結果。
     */
    public static Expression<String> targetEmailSubquery(
            Root<AdminOperationLog> root, CriteriaQuery<?> cq, CriteriaBuilder cb) {
        Subquery<String> userEmailSubquery = cq.subquery(String.class);
        Root<User> userEmailRoot = userEmailSubquery.from(User.class);
        userEmailSubquery.select(userEmailRoot.get("email"))
                .where(cb.equal(userEmailRoot.get("id"), root.get("targetId")));

        Subquery<String> eventOwnerEmailSubquery = cq.subquery(String.class);
        Root<MarketEvent> eventOwnerEmailRoot = eventOwnerEmailSubquery.from(MarketEvent.class);
        eventOwnerEmailSubquery.select(eventOwnerEmailRoot.get("user").get("email"))
                .where(cb.equal(eventOwnerEmailRoot.get("id"), root.get("targetId")));

        return cb.<String>selectCase()
                .when(cb.equal(root.get("targetType"), AdminTargetType.USER), userEmailSubquery)
                .when(root.get("targetType").in(AdminTargetType.MARKET_EVENT, AdminTargetType.EVENT_UNPUBLISH_REQUEST), eventOwnerEmailSubquery)
                .otherwise(cb.nullLiteral(String.class));
    }

    /**
     * 依targetId查出使用者角色(targetType不是USER時查不到人、回傳null)。<br>
     * 只回傳資料庫有實際對應欄位的原始Role，換算成AdminTargetTypeForFront的邏輯交給呼叫端在Java程式碼裡處理。
     */
    public static Expression<Role> targetUserRoleSubquery(
            Root<AdminOperationLog> root, CriteriaQuery<?> cq, CriteriaBuilder cb) {
        Subquery<Role> userRoleSubquery = cq.subquery(Role.class);
        Root<User> userRoleRoot = userRoleSubquery.from(User.class);
        userRoleSubquery.select(userRoleRoot.get("role"))
                .where(cb.equal(userRoleRoot.get("id"), root.get("targetId")));
        return userRoleSubquery;
    }
}
