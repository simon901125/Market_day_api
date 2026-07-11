package com.example.demo.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.demo.entity.User;
import com.example.demo.enums.Role;
import com.example.demo.enums.UserStatus;



public interface UserRepo extends JpaRepository<User, Long>, JpaSpecificationExecutor<User>{
    int countByRoleAndStatus(Role role, UserStatus status);
}
