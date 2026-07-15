package com.example.demo.Service;

import java.time.LocalDateTime;

import com.example.demo.enums.status.EventStatus;
import com.example.demo.enums.status.WorkflowStatus;

import jakarta.annotation.Nullable;

public interface EventStatusServiceInterface<T> {
    
    /** 確定活動目前在資料庫的狀態對應後要傳給前端的狀態，要和 {@link com.example.demo.Repository.specification.EventSpecification#withStatus}保持一致，修改時要一起改 */
    default EventStatus checkEventStatus(
        WorkflowStatus WorkflowStatus,
        LocalDateTime regStartTime,
        LocalDateTime regEndTime, 
        @Nullable LocalDateTime brandPublicTime, 
        LocalDateTime startTime,
        LocalDateTime endTime,
        int maxBooth,
        int nowBooth 
    ) {
        LocalDateTime now = LocalDateTime.now();
        switch (WorkflowStatus) {
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
                } else if (regStartTime.isBefore(now)
                        && regEndTime.isAfter(now)
                        && nowBooth <= maxBooth) {
                    return EventStatus.REGISTRATION_OPEN;
                }
                return EventStatus.FULL;
            case FINAL_REVIEW:
                if (brandPublicTime == null || brandPublicTime.isAfter(now)) {
                    return EventStatus.FULL;
                } else if (brandPublicTime.isBefore(now) && startTime.isAfter(now)) {
                    return EventStatus.PUBLISHED;
                } else if (startTime.isBefore(now) && endTime.isAfter(now)) {
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

    /** 把活動資料轉換成在前端顯示的活動狀態 */
    EventStatus changeToEventStatus(T data);

}
