package com.example.demo.Repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.Repository.projection.admin.AdminLookupProjection;
import com.example.demo.Repository.projection.admin.AdminOrganizerDetailProjection;
import com.example.demo.Repository.projection.admin.AdminVenderDetailProjection;
import com.example.demo.Repository.projection.admin.UserAccountStatusProjection;
import com.example.demo.entity.User;
import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.type.Role;



public interface UserRepo extends JpaRepository<User, Long>, JpaSpecificationExecutor<User>, UserRepoCustom {
    int countByRoleAndStatus(Role role, UserStatus status);
    Optional<User> findByEmail(String email);
    long countByIdAndRole(Long id, Role role);

    @Query("SELECT user.id FROM User user WHERE user.status = :status ORDER BY user.id")
    List<Long> findIdsByStatus(@Param("status") UserStatus status);

    @Query("SELECT user.id FROM User user WHERE user.role = :role AND user.status = :status ORDER BY user.id")
    List<Long> findIdsByRoleAndStatus(@Param("role") Role role, @Param("status") UserStatus status);

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
            WHERE user.id = :userId and user.role = 'VENDOR'
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
            WHERE user.id = :userId and user.role = 'ORGANIZER'
            """)
    Optional<AdminOrganizerDetailProjection> findOrganizerDetailById(@Param("userId") Long userId);

    /** 管理員後台: 依email與角色查詢操作者(管理員)身份，只查id與管理員名稱 */
    @Query("""
            SELECT new com.example.demo.Repository.projection.admin.AdminLookupProjection(
                user.id,
                adminProfile.name
            )
            FROM User user
            JOIN user.adminProfile adminProfile
            WHERE user.email = :email AND user.role = :role
            """)
    Optional<AdminLookupProjection> findAdminLookupByEmailAndRole(@Param("email") String email, @Param("role") Role role);

    /** 管理員後台: 使用者帳號停用/復原:查詢操作對象目前帳號狀態，只查id、狀態、email、聯絡人姓名 */
    @Query("""
            SELECT new com.example.demo.Repository.projection.admin.UserAccountStatusProjection(
                user.id,
                user.status,
                user.email,
                userProfile.contactName
            )
            FROM User user
            LEFT JOIN user.userProfile userProfile
            WHERE user.id = :userId
            """)
    Optional<UserAccountStatusProjection> findAccountStatusById(@Param("userId") Long userId);

    /** 管理員後台: 使用者帳號停用/復原:僅當目前狀態符合預期時才更新狀態，回傳影響筆數 */
    @Modifying
    @Query("UPDATE User user SET user.status = :newStatus, user.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE user.id = :userId AND user.status = :expectedStatus")
    int updateStatusIfCurrent(
            @Param("userId") Long userId,
            @Param("expectedStatus") UserStatus expectedStatus,
            @Param("newStatus") UserStatus newStatus);
}
