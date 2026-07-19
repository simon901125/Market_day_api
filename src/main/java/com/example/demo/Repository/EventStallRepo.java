package com.example.demo.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.EventStall;

public interface EventStallRepo extends JpaRepository<EventStall, Long> {
    long countByMarketEvent_Id(Long eventId);
}
