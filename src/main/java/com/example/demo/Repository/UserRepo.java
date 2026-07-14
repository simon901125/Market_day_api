package com.example.demo.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.Repository.projection.admin.AdminOrganizerDetailProjection;
import com.example.demo.Repository.projection.admin.AdminVenderDetailProjection;
import com.example.demo.entity.User;
import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.type.Role;



public interface UserRepo extends JpaRepository<User, Long>, JpaSpecificationExecutor<User>, UserRepoCustom {
    int countByRoleAndStatus(Role role, UserStatus status);

    /** 管理員後台: 攤主詳細:帳號與品牌基本資料 (不含最後登入時間、活動數統計，需另外查詢) */
    @Query("""
            SELECT new com.example.demo.Repository.projection.admin.AdminVenderDetailProjection(
                user.id,
                userProfile.contactName,
                user.role,
                user.status,
                user.provider,
                user.createdAt,
                vendorProfile.brandName,
                category.name,
                userProfile.contactPhone,
                userProfile.contactEmail,
                userProfile.city,
                userProfile.district,
                userProfile.address
            )
            FROM User user
            LEFT JOIN user.userProfile userProfile
            LEFT JOIN userProfile.vendorProfile vendorProfile
            LEFT JOIN vendorProfile.category category
            WHERE user.id = :userId
            """)
    Optional<AdminVenderDetailProjection> findVenderDetailById(@Param("userId") Long userId);

    /** 管理員後台: 主辦方詳細:帳號與主辦方基本資料 (不含最後登入時間、活動數統計，需另外查詢) */
    @Query("""
            SELECT new com.example.demo.Repository.projection.admin.AdminOrganizerDetailProjection(
                user.id,
                userProfile.contactName,
                user.role,
                user.status,
                user.provider,
                user.createdAt,
                organizerProfile.organizerName,
                organizerProfile.companyName,
                userProfile.contactPhone,
                userProfile.contactEmail,
                userProfile.city,
                userProfile.district,
                userProfile.address,
                organizerProfile.taxId,
                organizerProfile.serviceDays,
                organizerProfile.serviceStartTime,
                organizerProfile.serviceEndTime
            )
            FROM User user
            LEFT JOIN user.userProfile userProfile
            LEFT JOIN userProfile.organizerProfile organizerProfile
            WHERE user.id = :userId
            """)
    Optional<AdminOrganizerDetailProjection> findOrganizerDetailById(@Param("userId") Long userId);
}
