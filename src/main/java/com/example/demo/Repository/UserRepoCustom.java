package com.example.demo.Repository;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.example.demo.entity.User;

import jakarta.persistence.Tuple;

public interface UserRepoCustom {

    /** 管理員後台: 使用者搜尋列表 (只撈頁面需要用到的欄位，避免撈出整張表) */
    List<Tuple> findUserListTuples(Specification<User> spec, int pageNumber, int pageSize);
}
