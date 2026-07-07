package com.example.demo.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.User;
import com.example.demo.enums.Role;
import com.example.demo.entity.User.Status;



public interface UserRepo extends JpaRepository<User, Long>{
    int countByRole(Role role);
    int countByRoleAndStatus(Role role, Status status);
}
