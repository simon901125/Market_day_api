package com.example.demo.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.entity.MarketEvent;
import com.example.demo.enums.WorkflowStatus;

import java.time.LocalDateTime;

public interface EventRepo extends JpaRepository<MarketEvent, Long>, JpaSpecificationExecutor<MarketEvent> {
    int countByWorkflowStatus(WorkflowStatus workflowStatus);

    /** 計算(活動狀態=ACTIVE(前端:活動中))的數量 */
    @Query("select count(e.id) from MarketEvent e where e.workflowStatus = 'FINAL_REVIEW' and e.startAt <= :now and e.endAt >= :now")
    int countByEventStatusIsACTIVE(LocalDateTime now);

    /** 計算(活動狀態=已經在平台發布並且沒有下架也沒有結束)的數量 */
    @Query("select count(e.id) from MarketEvent e where (e.workflowStatus = 'PUBLISHED' or e.workflowStatus = 'FINAL_REVIEW') and e.publicInfoAt <= :now and e.endAt >= :now")
    int countByEventInPlatform(LocalDateTime now);

    /** 計算活動目前報名攤位(不計入被拒絕的攤位) */
    @Query("select count(a.id) from EventApplication a where a.event.id = :eventId and a.reviewStatus != 'REJECTED'")
    int countRegBoothsByEventId(@Param("eventId") Long eventId);
}
