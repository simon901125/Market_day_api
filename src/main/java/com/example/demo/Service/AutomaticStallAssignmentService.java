package com.example.demo.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Repository.AutomaticStallAssignmentRepository;
import com.example.demo.Repository.AutomaticStallAssignmentRepository.AssignmentEvent;
import com.example.demo.Repository.AutomaticStallAssignmentRepository.CompletedApplication;
import com.example.demo.Repository.AutomaticStallAssignmentRepository.PendingDate;

@Service
public class AutomaticStallAssignmentService {

    private final AutomaticStallAssignmentRepository repository;
    private final NotificationService notificationService;

    public AutomaticStallAssignmentService(
            AutomaticStallAssignmentRepository repository,
            NotificationService notificationService) {
        this.repository = repository;
        this.notificationService = notificationService;
    }

    @Transactional
    public boolean assignEvent(Long eventId, LocalDateTime now) {
        AssignmentEvent event = repository.lockDueEvent(eventId, now);
        if (event == null) {
            return false;
        }

        repository.cancelUnpaidApplications(eventId);

        Set<Long> changedApplicationIds = new LinkedHashSet<>();
        boolean allocationBlocked = false;
        List<PendingDate> pendingDates = repository.findPendingDates(eventId);
        for (PendingDate pendingDate : pendingDates) {
            Long stallId = repository.findFirstAvailableStall(eventId, pendingDate.applyDate());
            if (stallId == null) {
                allocationBlocked = true;
                break;
            }
            if (repository.assignStall(
                    pendingDate.applicationDateId(), pendingDate.applyDate(), stallId) != 1) {
                allocationBlocked = true;
                break;
            }
            changedApplicationIds.add(pendingDate.applicationId());
        }

        List<CompletedApplication> completed = repository.findCompletedApplications(
                eventId, List.copyOf(changedApplicationIds));
        for (CompletedApplication application : completed) {
            notificationService.notifyStallSelectionCompleted(
                    application.vendorUserId(),
                    application.applicationId(),
                    application.eventTitle());
            notificationService.notifyOrganizerStallSelectionCompleted(
                    event.organizerUserId(),
                    application.applicationId(),
                    application.eventTitle(),
                    application.brandName());
        }

        if (allocationBlocked || repository.countIncompleteEligibleApplications(eventId) != 0) {
            return false;
        }

        return repository.finishFinalReview(eventId, now) == 1;
    }
}
