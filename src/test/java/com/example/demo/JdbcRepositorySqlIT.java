package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Repository.ImageStorageRepository;
import com.example.demo.Repository.EventStallRepo;
import com.example.demo.Repository.OrganizerRepository;
import com.example.demo.Repository.PaymentRepository;
import com.example.demo.Repository.RequestLogRepository;
import com.example.demo.Repository.StallRepository;
import com.example.demo.dto.request.OrganizerEventSaveRequest;

@Tag("integration")
@Transactional
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JdbcRepositorySqlIT extends SqlServerIntegrationTestSupport {
    @Autowired StallRepository stall;
    @Autowired EventStallRepo eventStalls;
    @Autowired OrganizerRepository organizer;
    @Autowired PaymentRepository payment;
    @Autowired ImageStorageRepository images;
    @Autowired RequestLogRepository requests;
    @Autowired NamedParameterJdbcTemplate jdbc;

    @Test void stallReadQueriesCompileAgainstCurrentSchema() {
        assertThat(eventStalls.countByMarketEvent_Id(-1L)).isZero();
        assertThat(stall.findMarkets(null, null, null, "OPEN", null, null)).isNotNull();
        assertThat(stall.findMarkets(null, null, null, "FULL", null, null)).isNotNull();
        assertThat(stall.findPublishedMarketDetail(-1L)).isEmpty();
        assertThat(stall.findVendorApplications(-1L, null, null, null)).isEmpty();
        assertThat(stall.findStallId(-1L, "NONE")).isEmpty();
        assertThat(stall.findStallForSelection(-1L, "NONE")).isEmpty();
        assertThat(stall.findApplicationForSelection("NONE")).isEmpty();
        assertThat(stall.findSelectableApplication("NONE")).isEmpty();
        assertThat(stall.findApplicationDatesForSelection(-1L)).isEmpty();
        assertThat(stall.findVendorAccountByEmail("none@example.test")).isEmpty();
        assertThat(stall.findVendorProducts(-1L)).isEmpty();
        assertThat(stall.findActiveCategoriesByIds(List.of(-1L))).isEmpty();
        assertThat(stall.findSelectedStallApplication("NONE", LocalDate.now(), "NONE")).isEmpty();
        assertThat(stall.findSelectedApplicationDates("NONE")).isEmpty();
        assertThat(stall.findVendorStallMapApplication("NONE", LocalDate.now())).isEmpty();
        assertThat(stall.findEventStallsMap(-1L, LocalDate.now())).isEmpty();
        assertThat(stall.findOrganizerStallMapEvent(-1L, -1L)).isEmpty();
        assertThat(stall.findEventForStallStatus(-1L)).isEmpty();
        assertThat(stall.findOrganizerStallMapDetail(-1L, -1L, "NONE", LocalDate.now())).isEmpty();
        assertThat(stall.findEventStallsStatus(-1L, LocalDate.now())).isEmpty();
    }

    @Test void organizerReadQueriesCompileAgainstCurrentSchema() {
        assertThat(organizer.findOrganizerAccountByEmail("none@example.test")).isEmpty();
        assertThat(organizer.findOrganizerEvents(-1L, null, null, null)).isEmpty();
        assertThat(organizer.findOrganizerEventDetail(-1L, -1L)).isEmpty();
        assertThat(organizer.findOrganizerEventCategories(-1L)).isEmpty();
        assertThat(organizer.findOrganizerEventZones(-1L)).isEmpty();
        assertThat(organizer.findOrganizerAccountingEvents(-1L, null, null, null)).isEmpty();
        assertThat(organizer.findOrganizerAccountingEventDetail(-1L, -1L)).isEmpty();
        assertThat(organizer.findOrganizerAccountingPaymentDetails(-1L)).isEmpty();
        assertThat(organizer.findOrganizerStallEvents(-1L, null, null, null)).isEmpty();
        assertThat(organizer.findOrganizerEquipmentEvents(-1L, null, null, null)).isEmpty();
        assertThat(organizer.findOrganizerApplications(-1L, null, null, null, null)).isEmpty();
        assertThat(organizer.findOrganizerApplicationDetail(-1L, -1L)).isEmpty();
        assertThat(organizer.findVendorApplicationDetail(-1L, -1L)).isEmpty();
        assertThat(organizer.findApplicationStatusLogs(-1L)).isEmpty();
        assertThat(organizer.findApplicationEquipmentRentals(-1L)).isEmpty();
        assertThat(organizer.findApplicationDates(-1L)).isEmpty();
        assertThat(organizer.findOrganizerEquipmentEventDetail(-1L, -1L)).isEmpty();
        assertThat(organizer.findEventEquipments(-1L)).isEmpty();
        assertThat(organizer.findOrganizerEquipmentRentalStats(-1L)).isEmpty();
        assertThat(organizer.findOrganizerEquipmentManagementRows(-1L)).isEmpty();
        assertThat(organizer.findOrganizerPowerManagementRows(-1L)).isEmpty();
        assertThat(organizer.findOrganizerVehicleManagementRows(-1L)).isEmpty();
    }

    @Test void organizerEventWriteQueriesPersistCompleteDraft() {
        jdbc.update("""
                INSERT INTO dbo.users (role, email, provider, status, isLogin, email_verified_at)
                VALUES ('ORGANIZER', 'event-write-it@example.test', 'LOCAL', 'ACTIVE', 0, SYSDATETIME())
                """, Map.of());
        Long organizerUserId = jdbc.queryForObject(
                "SELECT id FROM dbo.users WHERE email = 'event-write-it@example.test'", Map.of(), Long.class);
        Long categoryId = jdbc.queryForObject(
                "SELECT TOP 1 id FROM dbo.categories WHERE is_active = 1 ORDER BY id", Map.of(), Long.class);
        LocalDateTime start = LocalDateTime.of(2026, 10, 10, 10, 0);
        OrganizerEventSaveRequest request = new OrganizerEventSaveRequest(
                null, "Integration Market", "Summary", "Description", List.of(categoryId),
                new OrganizerEventSaveRequest.Schedule(
                        start, start.plusDays(1), start.minusMonths(2), start.minusDays(1)),
                new OrganizerEventSaveRequest.Location(
                        "Venue", "臺北市", "信義區", "Address", "Metro", null, null),
                new OrganizerEventSaveRequest.Booth(
                        10, BigDecimal.valueOf(3), BigDecimal.valueOf(3), BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(500),
                        List.of(new OrganizerEventSaveRequest.Zone(null, "A 區", 10, "#F97316"))),
                new OrganizerEventSaveRequest.Equipment(false, true, false, List.of(
                        new OrganizerEventSaveRequest.Item(
                                null, null, "110", BigDecimal.ZERO, "DAY", null,
                                "FREE", "POWER", null, null, null, "ACTIVE", 1000))));

        Long eventId = organizer.createOrganizerEvent(organizerUserId, request);
        organizer.replaceEventCategories(eventId, request.categoryIds());
        organizer.replaceEventZones(eventId, request.booth().zones());
        organizer.replaceEventEquipment(eventId, request.equipment().items());

        assertThat(eventId).isPositive();
        assertThat(organizer.countActiveCategories(Set.of(categoryId))).isEqualTo(1);
        Map<String, Object> saved = organizer.findOrganizerEventDetail(organizerUserId, eventId).orElseThrow();
        assertThat(saved.get("providesEquipmentRental")).isEqualTo(false);
        assertThat(saved.get("providesBasicPower")).isEqualTo(true);
        assertThat(saved.get("allowsExtraPower")).isEqualTo(false);
        assertThat(organizer.findOrganizerEventCategories(eventId)).hasSize(1);
        assertThat(organizer.findOrganizerEventZones(eventId)).hasSize(1);
        assertThat(organizer.findEventEquipments(eventId)).hasSize(1);
        assertThat(images.updateEventImage(
                "event-write-it@example.test", eventId, "cover_image_url", "/images/cover.png")).isOne();
        jdbc.update("""
                UPDATE dbo.market_events
                SET workflow_status = N'PENDING_REVIEW', review_note = N'保留補件原因'
                WHERE id = :eventId
                """,
                Map.of("eventId", eventId));
        assertThat(images.updateEventImage(
                "event-write-it@example.test", eventId, "map_image_url", "/images/map.png")).isZero();
        assertThat(organizer.withdrawOrganizerEventReview(-1L, eventId)).isZero();
        assertThat(organizer.withdrawOrganizerEventReview(organizerUserId, eventId)).isOne();
        Map<String, Object> withdrawn = organizer.findOrganizerEventDetail(organizerUserId, eventId).orElseThrow();
        assertThat(withdrawn.get("workflowStatus")).isEqualTo("DRAFT");
        assertThat(withdrawn.get("reviewNote")).isEqualTo("保留補件原因");
        assertThat(organizer.withdrawOrganizerEventReview(organizerUserId, eventId)).isZero();

        Long zoneId = jdbc.queryForObject(
                "SELECT id FROM dbo.event_stall_zones WHERE event_id = :eventId",
                Map.of("eventId", eventId), Long.class);
        for (int number = 1; number <= 10; number++) {
            jdbc.update("""
                    INSERT INTO dbo.event_stalls (event_id, zone_id, stall_no, status)
                    VALUES (:eventId, :zoneId, :stallNo, N'AVAILABLE')
                    """, Map.of("eventId", eventId, "zoneId", zoneId, "stallNo", "A" + number));
        }
        jdbc.update("UPDATE dbo.market_events SET workflow_status = N'READY_TO_PUBLISH' WHERE id = :eventId",
                Map.of("eventId", eventId));
        LocalDateTime firstPublishedAt = LocalDateTime.of(2026, 7, 19, 18, 0);
        assertThat(organizer.countEventStalls(eventId)).isEqualTo(10);
        assertThat(organizer.publishOrganizerEvent(organizerUserId, eventId, firstPublishedAt)).isOne();
        Map<String, Object> published = organizer.findOrganizerEventDetail(organizerUserId, eventId).orElseThrow();
        assertThat(published.get("workflowStatus")).isEqualTo("PUBLISHED");
        assertThat(published.get("publicInfoAt")).isEqualTo(firstPublishedAt);
        assertThat(organizer.publishOrganizerEvent(organizerUserId, eventId, firstPublishedAt.plusDays(1))).isZero();

        LocalDateTime requestedAt = firstPublishedAt.plusHours(1);
        assertThat(organizer.requestOrganizerEventUnpublish(organizerUserId, eventId)).isOne();
        long unpublishRequestId = organizer.createEventUnpublishRequest(
                organizerUserId, eventId, "場地臨時無法使用", requestedAt);
        assertThat(unpublishRequestId).isPositive();
        Map<String, Object> unpublishRequested =
                organizer.findOrganizerEventDetail(organizerUserId, eventId).orElseThrow();
        assertThat(unpublishRequested.get("workflowStatus")).isEqualTo("UNPUBLISH_REQUESTED");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM dbo.event_unpublish_requests
                WHERE id = :requestId
                  AND event_id = :eventId
                  AND requested_by = :organizerUserId
                  AND reason = :reason
                  AND status = N'PENDING'
                """, Map.of(
                        "requestId", unpublishRequestId,
                        "eventId", eventId,
                        "organizerUserId", organizerUserId,
                        "reason", "場地臨時無法使用"), Integer.class)).isOne();
        assertThat(organizer.requestOrganizerEventUnpublish(organizerUserId, eventId)).isZero();
    }

    @Test void organizerEventDeleteChangesOnlyDraftStatusToCancelled() {
        jdbc.update("""
                INSERT INTO dbo.users (role, email, provider, status, isLogin, email_verified_at)
                VALUES ('ORGANIZER', 'event-delete-it@example.test', 'LOCAL', 'ACTIVE', 0, SYSDATETIME())
                """, Map.of());
        Long organizerUserId = jdbc.queryForObject(
                "SELECT id FROM dbo.users WHERE email = 'event-delete-it@example.test'", Map.of(), Long.class);
        jdbc.update("""
                INSERT INTO dbo.market_events (user_id, title, workflow_status)
                VALUES (:organizerUserId, N'可刪除草稿', N'DRAFT')
                """, Map.of("organizerUserId", organizerUserId));
        Long eventId = jdbc.queryForObject("""
                SELECT id FROM dbo.market_events
                WHERE user_id = :organizerUserId AND title = N'可刪除草稿'
                """, Map.of("organizerUserId", organizerUserId), Long.class);

        Map<String, Object> locked = organizer
                .findOrganizerEventForDeletion(organizerUserId, eventId).orElseThrow();
        assertThat(locked.get("workflowStatus")).isEqualTo("DRAFT");
        assertThat(organizer.cancelDraftOrganizerEvent(organizerUserId, eventId)).isOne();
        assertThat(organizer.cancelDraftOrganizerEvent(organizerUserId, eventId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.market_events WHERE id = :eventId",
                Map.of("eventId", eventId), Integer.class)).isOne();
        assertThat(organizer.findOrganizerEventDetail(organizerUserId, eventId)).isEmpty();
        assertThat(organizer.findOrganizerEvents(organizerUserId, null, null, null))
                .noneMatch(event -> eventId.equals(((Number) event.get("eventId")).longValue()));
    }

    @Test void paymentReadQueriesCompileAgainstCurrentSchema() {
        assertThat(payment.findVendorPaymentUserByEmail("none@example.test")).isEmpty();
        assertThat(payment.findOrganizerPaymentUserByEmail("none@example.test")).isEmpty();
        assertThat(payment.findPayableApplication("NONE")).isEmpty();
        assertThat(payment.findRefundableApplication("NONE")).isEmpty();
        assertThat(payment.findPaymentStatusByApplicationNo("NONE")).isEmpty();
        assertThat(payment.findLatestPendingPayment(-1L)).isEmpty();
        assertThat(payment.findLatestPaidPayment(-1L)).isEmpty();
        assertThat(payment.findLatestRefundByApplicationId(-1L)).isEmpty();
        assertThat(payment.findPaymentWithApplication("NONE")).isEmpty();
        assertThat(payment.findRefundForOrganizerProcessing("NONE")).isEmpty();
    }

    @Test void imageUpdateQueriesUseAllowedColumnsAndOwnershipChecks() {
        assertThat(images.updateVendorImage("none@example.test", "avatar_image_url", "/images/a.png")).isZero();
        assertThat(images.updateVendorImage("none@example.test", "cover_image_url", "/images/a.png")).isZero();
        assertThat(images.updateProductImage("none@example.test", -1L, "/images/a.png")).isZero();
        assertThat(images.updateEventImage("none@example.test", -1L, "cover_image_url", "/images/a.png")).isZero();
        assertThat(images.updateEventImage("none@example.test", -1L, "map_image_url", "/images/a.png")).isZero();
    }

    @Test void requestLogInsertWorksWithAnonymousRequest() {
        Long id = requests.createRequestLog(null, "POST", "/api/test", 200);
        assertThat(id).isPositive();
    }
}
