package com.example.demo.Repository;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.MarketEvent;
import com.example.demo.enums.WorkflowStatus;
import java.util.List;
import java.time.LocalDateTime;


public interface ActivityRepo extends JpaRepository<MarketEvent, Long>{
    int countByWorkflowStatus(WorkflowStatus workflowStatus);
    int countByWorkflowStatusIn(Collection<WorkflowStatus> publishStatuses);
    List<MarketEvent> findByStartAt(LocalDateTime startAt);
    List<MarketEvent> findByEndAt(LocalDateTime endAt);
    
}
