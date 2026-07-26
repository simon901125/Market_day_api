package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.example.demo.enums.status.EventStatus;
import com.example.demo.enums.status.WorkflowStatus;

class EventStatusServiceInterfaceTest {

    private final EventStatusServiceInterface<Object> service = new EventStatusServiceInterface<>() {
        @Override
        public EventStatus changeToEventStatus(Object data) {
            return null;
        }
    };

    @Test
    void mapsNonTemporalWorkflowStatuses() {
        LocalDateTime now = LocalDateTime.now();

        assertThat(status(WorkflowStatus.DRAFT, now, 0, 10)).isEqualTo(EventStatus.DRAFT);
        assertThat(status(WorkflowStatus.PENDING_REVIEW, now, 0, 10)).isEqualTo(EventStatus.PENDING_REVIEW);
        assertThat(status(WorkflowStatus.REVISION_REQUIRED, now, 0, 10)).isEqualTo(EventStatus.REVISION_REQUIRED);
        assertThat(status(WorkflowStatus.MAP_BUILDING, now, 0, 10)).isEqualTo(EventStatus.MAP_BUILDING);
        assertThat(status(WorkflowStatus.READY_TO_PUBLISH, now, 0, 10)).isEqualTo(EventStatus.READY_TO_PUBLISH);
        assertThat(status(WorkflowStatus.UNPUBLISH_REQUESTED, now, 0, 10)).isEqualTo(EventStatus.UNPUBLISH_REQUESTED);
        assertThat(status(WorkflowStatus.UNPUBLISHED, now, 0, 10)).isEqualTo(EventStatus.UNPUBLISHED);
        assertThat(status(WorkflowStatus.CANCELLED, now, 0, 10)).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void derivesPublishedRegistrationStatusFromDatesAndCapacity() {
        LocalDateTime now = LocalDateTime.now();

        assertThat(status(WorkflowStatus.PUBLISHED, now.plusDays(1), 0, 10))
                .isEqualTo(EventStatus.PUBLISHED);
        assertThat(status(WorkflowStatus.PUBLISHED, now.minusHours(1), 5, 10))
                .isEqualTo(EventStatus.REGISTRATION_OPEN);
        assertThat(status(WorkflowStatus.PUBLISHED, now.minusHours(1), 10, 10))
                .isEqualTo(EventStatus.FULL);
        assertThat(status(WorkflowStatus.PUBLISHED, now.minusHours(1), 11, 10))
                .isEqualTo(EventStatus.FULL);
    }

    @Test
    void derivesFinalReviewPublicActiveAndEndedStatuses() {
        LocalDateTime now = LocalDateTime.now();

        assertThat(service.checkEventStatus(WorkflowStatus.FINAL_REVIEW,
                now.minusDays(3), now.minusDays(2), null, now.plusDays(1), now.plusDays(2), 10, 10))
                .isEqualTo(EventStatus.FINAL_CONFIRMATION);
        assertThat(service.checkEventStatus(WorkflowStatus.FINAL_REVIEW,
                now.minusDays(3), now.minusDays(2), now.plusHours(1), now.plusDays(1), now.plusDays(2), 10, 10))
                .isEqualTo(EventStatus.FINAL_CONFIRMATION);
        assertThat(service.checkEventStatus(WorkflowStatus.FINAL_REVIEW,
                now.minusDays(3), now.minusDays(2), now.minusHours(1), now.plusDays(1), now.plusDays(2), 10, 10))
                .isEqualTo(EventStatus.BRANDS_PUBLISHED);
        assertThat(service.checkEventStatus(WorkflowStatus.FINAL_REVIEW,
                now.minusDays(3), now.minusDays(2), now.minusDays(2), now.minusHours(1), now.plusHours(1), 10, 10))
                .isEqualTo(EventStatus.ACTIVE);
        assertThat(service.checkEventStatus(WorkflowStatus.FINAL_REVIEW,
                now.minusDays(3), now.minusDays(2), now.minusDays(2), now.minusDays(1), now.minusHours(1), 10, 10))
                .isEqualTo(EventStatus.ENDED);
    }

    private EventStatus status(WorkflowStatus workflowStatus, LocalDateTime registrationStart,
            int currentBooths, int maxBooths) {
        LocalDateTime now = LocalDateTime.now();
        return service.checkEventStatus(workflowStatus,
                registrationStart, now.plusDays(2), now.plusDays(3), now.plusDays(4), now.plusDays(5),
                maxBooths, currentBooths);
    }
}
