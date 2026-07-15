package com.example.demo.Repository;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.example.demo.entity.MarketEvent;

import jakarta.persistence.Tuple;

public interface EventRepoCustom {

    /** 管理員後台: 活動搜尋列表 (只撈頁面需要用到的欄位，避免撈出整張表) */
    List<Tuple> findEventListTuples(Specification<MarketEvent> spec, int pageNumber, int pageSize);
}
