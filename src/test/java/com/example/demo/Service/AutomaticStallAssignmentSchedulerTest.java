package com.example.demo.Service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import com.example.demo.Repository.AutomaticStallAssignmentRepository;

@ExtendWith(MockitoExtension.class)
class AutomaticStallAssignmentSchedulerTest {

    @Mock AutomaticStallAssignmentRepository repository;
    @Mock AutomaticStallAssignmentService assignmentService;
    @InjectMocks AutomaticStallAssignmentScheduler scheduler;

    @Test
    void processesEveryClosedPublishedEvent() {
        when(repository.findDueEventIds(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(1L, 2L));

        scheduler.assignClosedEvents();

        verify(assignmentService).assignEvent(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any());
        verify(assignmentService).assignEvent(
                org.mockito.ArgumentMatchers.eq(2L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void runsOnceAtMidnightInTaipeiTime() throws NoSuchMethodException {
        Scheduled scheduled = AutomaticStallAssignmentScheduler.class
                .getMethod("assignClosedEvents")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 0 0 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Taipei");
    }
}
