package com.example.demo.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.Repository.projection.admin.UserLoginLogProjection;
import com.example.demo.entity.RequestLog;

public interface RequestLogRepo extends JpaRepository<RequestLog, Long> {

    /** 管理員後台: 使用者詳細:登入紀錄列表 (依請求時間新到舊) */
    @Query("""
            SELECT new com.example.demo.Repository.projection.admin.UserLoginLogProjection(
                r.createdAt,
                r.path,
                r.statusCode
            )
            FROM RequestLog r
            WHERE r.user.id = :userId
                AND r.path IN :paths
            ORDER BY r.createdAt DESC
            """)
    List<UserLoginLogProjection> findUserLoginLogs(
            @Param("userId") Long userId, @Param("paths") List<String> paths, Pageable pageable);

    /** 計算指定使用者的登入紀錄總筆數 */
    @Query("select count(r.id) from RequestLog r where r.user.id = :userId and r.path in :paths")
    long countUserLoginLogs(@Param("userId") Long userId, @Param("paths") List<String> paths);

    /** 管理員後台: 使用者詳細:帳號最後登入時間 */
    @Query("select max(r.createdAt) from RequestLog r where r.user.id = :userId and r.path in :paths")
    LocalDateTime findLastLoginAt(@Param("userId") Long userId, @Param("paths") List<String> paths);
}
