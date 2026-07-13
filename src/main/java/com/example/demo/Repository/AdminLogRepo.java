package com.example.demo.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.demo.entity.AdminOperationLog;

public interface AdminLogRepo extends JpaRepository<AdminOperationLog, Long>,  JpaSpecificationExecutor<AdminOperationLog>{

}
