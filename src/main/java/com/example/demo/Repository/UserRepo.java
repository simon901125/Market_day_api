package com.example.demo.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.entity.User;
import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.type.Role;
import com.example.demo.projection.admin.AdminVenderDetailProjection;



public interface UserRepo extends JpaRepository<User, Long>, JpaSpecificationExecutor<User>{
    int countByRoleAndStatus(Role role, UserStatus status);
    // @Query("""
    //         SELECT 
    //             user.id id,
    //             userProfile.name,
    //             user.role,
    //             user.status,
    //             user.provider,
    //             user.createdAt,


            
    //         FROM User user
    //         LEFT JOIN user.userProfile userProfile
    //         WHERE user.id = :id
    //         """)
    // Optional<AdminVenderDetailProjection>findVenderDetailById(@Param("id") Long id);
}
