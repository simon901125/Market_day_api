package com.example.demo.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.demo.Repository.AutomaticStallAssignmentRepository;

@Service
public class AutomaticStallAssignmentScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutomaticStallAssignmentScheduler.class);
    private static final String TAIPEI_TIME_ZONE = "Asia/Taipei";

    private final AutomaticStallAssignmentRepository repository;
    private final AutomaticStallAssignmentService assignmentService;

    public AutomaticStallAssignmentScheduler(
            AutomaticStallAssignmentRepository repository,
            AutomaticStallAssignmentService assignmentService) {
        this.repository = repository;
        this.assignmentService = assignmentService;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = TAIPEI_TIME_ZONE)
    public void assignClosedEvents() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of(TAIPEI_TIME_ZONE));
        List<Long> eventIds = repository.findDueEventIds(now);
        for (Long eventId : eventIds) {
            try {
                assignmentService.assignEvent(eventId, now);
            } catch (RuntimeException exception) {
                LOGGER.error("Automatic stall assignment failed for event {}", eventId, exception);
            }
        }
    }
}
