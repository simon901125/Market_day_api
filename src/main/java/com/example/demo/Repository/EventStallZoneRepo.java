package com.example.demo.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.EventStallZone;

public interface EventStallZoneRepo extends JpaRepository<EventStallZone, Long> {

    /** 依活動編號查詢攤位分區清單 */
    List<EventStallZone> findByMarketEventId(Long marketEventId);
}
