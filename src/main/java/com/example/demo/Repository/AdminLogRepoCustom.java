package com.example.demo.Repository;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.example.demo.entity.AdminOperationLog;

import jakarta.persistence.Tuple;

public interface AdminLogRepoCustom {

    /** 管理員後台: 操作紀錄搜尋列表 (只撈頁面需要用到的欄位，避免撈出整張表) */
    List<Tuple> findLogListTuples(Specification<AdminOperationLog> spec, int pageNumber, int pageSize);
}
