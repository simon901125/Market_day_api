package com.example.demo.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.example.demo.entity.User;
import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.type.Role;



public interface UserRepo extends JpaRepository<User, Long>, JpaSpecificationExecutor<User>, UserRepoCustom {
    int countByRoleAndStatus(Role role, UserStatus status);
}
