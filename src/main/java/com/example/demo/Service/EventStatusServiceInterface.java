package com.example.demo.Service;

import java.time.LocalDateTime;

import com.example.demo.entity.MarketEvent;
import com.example.demo.enums.EventStatus;

public interface EventStatusServiceInterface {
    /**確定活動目前在資料庫的狀態對應後要傳給前端的狀態 */
    default EventStatus checkEventStatus(MarketEvent event){
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime regStartTime = event.getRegistrationStartAt();
        LocalDateTime regEndTime = event.getRegistrationEndAt();
        LocalDateTime startTime = event.getStartAt();
        LocalDateTime endTime = event.getEndAt();
        
        switch (event.getWorkflowStatus()) {
            case DRAFT:
                return EventStatus.DRAFT;
            case PENDING_REVIEW:
                return EventStatus.PENDING_REVIEW;
            case REVISION_REQUIRED:
                return EventStatus.REVISION_REQUIRED;
            case MAP_BUILDING:
                return EventStatus.MAP_BUILDING;
            case READY_TO_PUBLISH:
                return EventStatus.READY_TO_PUBLISH;
            case PUBLISHED:
                if (regStartTime.isAfter(now)) {
                    return EventStatus.READY_TO_PUBLISH;
                }else if(regStartTime.isBefore(now) 
                    && regEndTime.isAfter(now) 
                    && event.getEventApplications().size() <= event.getMaxBooths()){
                    return EventStatus.REGISTRATION_OPEN;
                }
                return EventStatus.FULL;
            case FINAL_REVIEW:
                if (startTime.isAfter(now)) {
                    return EventStatus.FULL;
                } else if(startTime.isBefore(now) && endTime.isAfter(now)){
                    return EventStatus.ACTIVE;
                }
                return EventStatus.ENDED;
            case UNPUBLISH_REQUESTED:
                return EventStatus.UNPUBLISH_REQUESTED;
            case UNPUBLISHED:
                return EventStatus.UNPUBLISHED;
            case CANCELLED:
                return null;
            default:
                break;
        }


















        return null;
    }
}
