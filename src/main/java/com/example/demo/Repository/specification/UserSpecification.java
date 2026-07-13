package com.example.demo.Repository.specification;

import org.springframework.data.jpa.domain.Specification;

import com.example.demo.dto.request.admin.AdminUserSearchDto;
import com.example.demo.entity.RequestLog;
import com.example.demo.entity.User;
import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.type.Role;

public class UserSpecification {

    public static Specification<User> build(AdminUserSearchDto request) {
        return Specification.allOf(
                withKeyword(request.keyWord()),
                withRole(request.role()),
                withStatus(request.status()));
    }

    /**
     * 使用者名稱/Email模糊搜尋
     * @param keyword 關鍵字
     * @return WHERE `user.userProfile.name` LIKE "%keyword%" OR `user.email` LIKE "%keyword%"
     */
    private static Specification<User> withKeyword(String keyword) {

        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            return cb.or(
                cb.like(cb.lower(root.get("userProfile").get("name")), "%" + keyword.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("email")), "%" + keyword.toLowerCase() + "%")
            );
        };
    }
    /**
     * 使用者角色搜尋
     * @param role 角色類型
     * @return WHERE `user.role` = "role"
     */
    private static Specification<User> withRole(Role role) {

        return (root, cq, cb) -> {
            if (role == null) {
                return null;
            }
            return cb.equal(root.get("role"), role);
        };
    }

    /**
     * 使用者帳號狀態搜尋
     * @param status 使用者帳號狀態
     * @return
     */
    private static Specification<User> withStatus(UserStatus status) {

        return(root, cq, cb) -> {

            if (status == null) {
                return null;
            }
            return cb.equal(root.get("status"), status);
        };
    }
    //TODO:子查詢->用userId查最後登入時間，select requestLog.createdAt from RequestLog，where use.id = :id, requestLog.statusCode = 200, path =''
}
