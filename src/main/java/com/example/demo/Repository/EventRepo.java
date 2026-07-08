package com.example.demo.Repository;

import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.entity.MarketEvent;
import com.example.demo.enums.WorkflowStatus;
import com.example.demo.projection.admin.AdminEventItemProjection;

import java.util.List;
import java.time.LocalDateTime;


public interface EventRepo extends JpaRepository<MarketEvent, Long>{
    int countByWorkflowStatus(WorkflowStatus workflowStatus);
    int countByWorkflowStatusIn(Collection<WorkflowStatus> publishStatuses);
    List<MarketEvent> findByStartAt(LocalDateTime startAt);
    List<MarketEvent> findByEndAt(LocalDateTime endAt);
    Page<AdminEventItemProjection> findAllByOrderByCreateAtDesc(Pageable pageable);


    /**計算活動目前報名攤位(不計入被拒絕的攤位) */
    @Query("select count(a.id) from EventApplication a where a.event.id = :eventId and a.reviewStatus != 'REJECTED'")
    int countRegBoothsByEventId(@Param("eventId") Long eventId);



    /**對應EventStatus.ACTIVE：workflowStatus為FINAL_REVIEW且目前時間介於startAt與endAt之間 */
    int countByWorkflowStatusAndStartAtBeforeAndEndAtAfter(
        WorkflowStatus workflowStatus, LocalDateTime start, LocalDateTime end);
    /**對應活動公布中：workflowStatus為PUBLISHED且目前時間在endAt之前 */
    int countByWorkflowStatusAndEndAtAfter(WorkflowStatus workflowStatus, LocalDateTime end);

    
}
