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
    @Autowired OrganizerRepository organizer;
    @Autowired PaymentRepository payment;
    @Autowired ImageStorageRepository images;
    @Autowired RequestLogRepository requests;
    @Autowired NamedParameterJdbcTemplate jdbc;

    @Test void stallReadQueriesCompileAgainstCurrentSchema() {
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
                new OrganizerEventSaveRequest.Equipment(List.of()));

        Long eventId = organizer.createOrganizerEvent(organizerUserId, request);
        organizer.replaceEventCategories(eventId, request.categoryIds());
        organizer.replaceEventZones(eventId, request.booth().zones());
        organizer.replaceEventEquipment(eventId, List.of());

        assertThat(eventId).isPositive();
        assertThat(organizer.countActiveCategories(Set.of(categoryId))).isEqualTo(1);
        assertThat(organizer.findOrganizerEventDetail(organizerUserId, eventId)).isPresent();
        assertThat(organizer.findOrganizerEventCategories(eventId)).hasSize(1);
        assertThat(organizer.findOrganizerEventZones(eventId)).hasSize(1);
        assertThat(images.updateEventImage(
                "event-write-it@example.test", eventId, "cover_image_url", "/images/cover.png")).isOne();
        jdbc.update("UPDATE dbo.market_events SET workflow_status = N'PENDING_REVIEW' WHERE id = :eventId",
                Map.of("eventId", eventId));
        assertThat(images.updateEventImage(
                "event-write-it@example.test", eventId, "map_image_url", "/images/map.png")).isZero();
    }

    @Test void paymentReadQueriesCompileAgainstCurrentSchema() {
        assertThat(payment.findPayableApplication("NONE")).isEmpty();
        assertThat(payment.findPaymentStatusByApplicationNo("NONE")).isEmpty();
        assertThat(payment.findLatestPendingPayment(-1L)).isEmpty();
        assertThat(payment.findPaymentWithApplication("NONE")).isEmpty();
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
