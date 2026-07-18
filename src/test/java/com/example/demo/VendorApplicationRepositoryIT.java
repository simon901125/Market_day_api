package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Repository.StallRepository;
import com.example.demo.Repository.OrganizerRepository;
import com.example.demo.Repository.UserRepository;

@Tag("integration")
@Transactional
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class VendorApplicationRepositoryIT extends SqlServerIntegrationTestSupport {

    @Autowired StallRepository repository;
    @Autowired OrganizerRepository organizerRepository;
    @Autowired UserRepository userRepository;
    @Autowired NamedParameterJdbcTemplate jdbc;

    @Test
    void returnsOnlyCurrentVendorRecordsInSubmittedOrder() {
        Long organizerId = createUser("ORGANIZER", "application-organizer@example.test");
        Vendor vendor = createVendor("application-vendor@example.test", "Integration Vendor");
        Vendor otherVendor = createVendor("other-application-vendor@example.test", "Other Vendor");
        LocalDateTime sameSubmittedAt = LocalDateTime.of(2026, 7, 1, 12, 0);

        Long firstEvent = createEvent(organizerId, "Integration Spring Market",
                LocalDateTime.of(2026, 8, 10, 10, 0));
        Long secondEvent = createEvent(organizerId, "Integration Summer Market",
                LocalDateTime.of(2026, 8, 20, 10, 0));
        Long firstApplication = createApplication("VENDOR-APP-001", firstEvent, vendor, sameSubmittedAt);
        Long secondApplication = createApplication("VENDOR-APP-002", secondEvent, vendor, sameSubmittedAt);
        createApplication("OTHER-VENDOR-APP", secondEvent, otherVendor, sameSubmittedAt.plusHours(1));
        jdbc.update("""
                INSERT INTO refunds (refund_no, application_id, amount, refund_status, refunded_at)
                VALUES (N'VENDOR-REFUND-001', :applicationId, 100, N'REFUNDED', SYSDATETIME())
                """, Map.of("applicationId", secondApplication));

        List<Map<String, Object>> rows = repository.findVendorApplications(vendor.userId(), null, null, null);

        assertThat(rows).hasSize(2);
        assertThat(((Number) rows.get(0).get("applicationId")).longValue()).isEqualTo(secondApplication);
        assertThat(((Number) rows.get(1).get("applicationId")).longValue()).isEqualTo(firstApplication);
        assertThat(rows.get(0))
                .containsEntry("applicationNo", "VENDOR-APP-002")
                .containsEntry("eventTitle", "Integration Summer Market")
                .containsEntry("location", "台北市 Integration Venue")
                .containsEntry("refundStatus", "REFUNDED");
    }

    @Test
    void appliesEventTitleAndInclusiveDateFilters() {
        Long organizerId = createUser("ORGANIZER", "filter-organizer@example.test");
        Vendor vendor = createVendor("filter-vendor@example.test", "Filter Vendor");
        Long augustEvent = createEvent(organizerId, "August Integration Market",
                LocalDateTime.of(2026, 8, 15, 10, 0));
        Long septemberEvent = createEvent(organizerId, "September Integration Market",
                LocalDateTime.of(2026, 9, 15, 10, 0));
        createApplication("FILTER-APP-001", augustEvent, vendor, LocalDateTime.of(2026, 7, 1, 10, 0));
        createApplication("FILTER-APP-002", septemberEvent, vendor, LocalDateTime.of(2026, 7, 2, 10, 0));

        List<Map<String, Object>> rows = repository.findVendorApplications(
                vendor.userId(),
                "August",
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 9, 1, 0, 0));

        assertThat(rows).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("applicationNo", "FILTER-APP-001"));
    }

    @Test
    void detailRequiresApplicationOwnershipAndReturnsScreenHeaderData() {
        Long organizerId = createUser("ORGANIZER", "detail-organizer@example.test");
        Vendor vendor = createVendor("detail-vendor@example.test", "Detail Vendor");
        Vendor otherVendor = createVendor("detail-other-vendor@example.test", "Other Detail Vendor");
        Long eventId = createEvent(organizerId, "Detail Integration Market",
                LocalDateTime.of(2026, 8, 15, 10, 0));
        Long applicationId = createApplication(
                "DETAIL-APP-001", eventId, vendor, LocalDateTime.of(2026, 7, 1, 10, 0));

        var owned = organizerRepository.findVendorApplicationDetail(vendor.userId(), applicationId);
        var notOwned = organizerRepository.findVendorApplicationDetail(otherVendor.userId(), applicationId);

        assertThat(owned).isPresent();
        assertThat(owned.orElseThrow())
                .containsEntry("applicationNo", "DETAIL-APP-001")
                .containsEntry("eventTitle", "Detail Integration Market")
                .containsEntry("eventCoverImageUrl", "/images/integration-event.jpg");
        assertThat(notOwned).isEmpty();
    }

    private Long createUser(String role, String email) {
        Long userId = userRepository.createLocalUser(role, email, "hash");
        userRepository.markEmailVerified(userId);
        return userId;
    }

    private Vendor createVendor(String email, String brandName) {
        Long userId = createUser("VENDOR", email);
        Long categoryId = jdbc.queryForObject("SELECT TOP 1 id FROM categories ORDER BY id", Map.of(), Long.class);
        userRepository.createUserProfile(userId, "VENDOR", brandName, email);
        Long userProfileId = jdbc.queryForObject(
                "SELECT id FROM user_profiles WHERE user_id = :userId AND profile_type = N'VENDOR'",
                Map.of("userId", userId), Long.class);
        jdbc.update("""
                INSERT INTO vendor_profiles (user_profile_id, category_id, brand_name)
                VALUES (:userProfileId, :categoryId, :brandName)
                """, new MapSqlParameterSource()
                .addValue("userProfileId", userProfileId)
                .addValue("categoryId", categoryId)
                .addValue("brandName", brandName));
        Long vendorProfileId = jdbc.queryForObject(
                "SELECT id FROM vendor_profiles WHERE user_profile_id = :userProfileId",
                Map.of("userProfileId", userProfileId), Long.class);
        return new Vendor(userId, vendorProfileId);
    }

    private Long createEvent(Long organizerId, String title, LocalDateTime startAt) {
        Long categoryId = jdbc.queryForObject("SELECT TOP 1 id FROM categories ORDER BY id", Map.of(), Long.class);
        LocalDateTime endAt = startAt.plusDays(1);
        Long eventId = jdbc.queryForObject("""
                INSERT INTO market_events (
                    user_id, title, summary, description,
                    location_name, city, address,
                    start_at, end_at, registration_start_at, registration_end_at,
                    max_booths, base_fee, cover_image_url, workflow_status
                )
                OUTPUT INSERTED.id
                VALUES (
                    :organizerId, :title, N'Integration summary', N'Integration description',
                    N'Integration Venue', N'台北市', N'Integration address',
                    :startAt, :endAt, :registrationStartAt, :registrationEndAt,
                    20, 1000, N'/images/integration-event.jpg', N'PUBLISHED'
                )
                """, new MapSqlParameterSource()
                .addValue("organizerId", organizerId)
                .addValue("title", title)
                .addValue("startAt", startAt)
                .addValue("endAt", endAt)
                .addValue("registrationStartAt", startAt.minusMonths(2))
                .addValue("registrationEndAt", startAt.minusDays(1)), Long.class);
        jdbc.update("""
                INSERT INTO market_event_categories (event_id, category_id)
                VALUES (:eventId, :categoryId)
                """, Map.of("eventId", eventId, "categoryId", categoryId));
        return eventId;
    }

    private Long createApplication(String applicationNo, Long eventId, Vendor vendor, LocalDateTime createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO event_applications (
                    application_no, event_id, user_id, vendor_profile_id,
                    total_amount, review_status, payment_status, created_at
                )
                OUTPUT INSERTED.id
                VALUES (
                    :applicationNo, :eventId, :userId, :vendorProfileId,
                    1000, N'APPROVED', N'PAID', :createdAt
                )
                """, new MapSqlParameterSource()
                .addValue("applicationNo", applicationNo)
                .addValue("eventId", eventId)
                .addValue("userId", vendor.userId())
                .addValue("vendorProfileId", vendor.vendorProfileId())
                .addValue("createdAt", createdAt), Long.class);
    }

    private record Vendor(Long userId, Long vendorProfileId) {
    }
}
