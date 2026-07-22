package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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

import com.example.demo.Repository.AutomaticStallAssignmentRepository;
import com.example.demo.Repository.MarketEventRepository;
import com.example.demo.Repository.UserRepository;
import com.example.demo.Service.AutomaticStallAssignmentScheduler;

@Tag("integration")
@Transactional
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AutomaticStallAssignmentFlowIT extends SqlServerIntegrationTestSupport {

    @Autowired AutomaticStallAssignmentScheduler scheduler;
    @Autowired AutomaticStallAssignmentRepository assignmentRepository;
    @Autowired MarketEventRepository marketEventRepository;
    @Autowired UserRepository userRepository;
    @Autowired NamedParameterJdbcTemplate jdbc;

    @Test
    void schedulerCancelsUnpaidAssignsPaidPublishesBrandsAndIsIdempotent() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        TestEvent event = createDueEvent("AUTO-FLOW", now, 2);
        TestVendor firstPaid = createVendor("auto-flow-paid-1@example.test", "自動選位品牌一");
        TestVendor secondPaid = createVendor("auto-flow-paid-2@example.test", "自動選位品牌二");
        TestVendor unpaid = createVendor("auto-flow-unpaid@example.test", "未付款品牌");
        Long firstApplication = createApplication(
                "AUTO-FLOW-PAID-1", event.id(), firstPaid, "PAID", event.eventDate());
        Long secondApplication = createApplication(
                "AUTO-FLOW-PAID-2", event.id(), secondPaid, "PAID", event.eventDate());
        Long unpaidApplication = createApplication(
                "AUTO-FLOW-UNPAID", event.id(), unpaid, "PENDING", event.eventDate());

        scheduler.assignClosedEvents();

        assertThat(isCancelled(unpaidApplication)).isTrue();
        assertThat(isCancelled(firstApplication)).isFalse();
        assertThat(isCancelled(secondApplication)).isFalse();

        Map<Long, Long> firstAssignments = selectedStalls(firstApplication, secondApplication);
        assertThat(firstAssignments).hasSize(2);
        assertThat(firstAssignments.values()).doesNotHaveDuplicates();

        Map<String, Object> eventRow = eventState(event.id());
        assertThat(eventRow).containsEntry("workflowStatus", "FINAL_REVIEW");
        assertThat(eventRow.get("brandsPublicAt")).isNotNull();
        assertThat(((java.sql.Timestamp) eventRow.get("brandsPublicAt")).toLocalDateTime())
                .isBetween(now, LocalDateTime.now().plusSeconds(1));
        assertThat(assignmentRepository.findDueEventIds(LocalDateTime.now())).doesNotContain(event.id());

        var detail = marketEventRepository.findMarketEventDetailById(event.id()).orElseThrow();
        assertThat(detail.brandsPublic()).isTrue();
        assertThat(detail.mapImageUrl()).isEqualTo("/images/auto-flow-map.jpg");

        String firstStallNo = stallNo(firstAssignments.get(firstApplication));
        var publicStall = marketEventRepository.findPublicSelectedStall(
                event.id(), event.eventDate(), firstStallNo).orElseThrow();
        assertThat(publicStall.brand()).isNotNull();
        assertThat(publicStall.brand().brandName()).isEqualTo("自動選位品牌一");

        int notificationCount = notificationCount(event.id());
        assertThat(notificationCount).isEqualTo(4);

        scheduler.assignClosedEvents();

        assertThat(selectedStalls(firstApplication, secondApplication)).isEqualTo(firstAssignments);
        assertThat(notificationCount(event.id())).isEqualTo(notificationCount);
        assertThat(isCancelled(unpaidApplication)).isTrue();
    }

    @Test
    void insufficientStallsNeverAssignsOneStallTwiceAndKeepsFinalReviewPending() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        TestEvent event = createDueEvent("AUTO-LIMIT", now, 1);
        TestVendor firstPaid = createVendor("auto-limit-paid-1@example.test", "攤位不足品牌一");
        TestVendor secondPaid = createVendor("auto-limit-paid-2@example.test", "攤位不足品牌二");
        Long firstApplication = createApplication(
                "AUTO-LIMIT-PAID-1", event.id(), firstPaid, "PAID", event.eventDate());
        Long secondApplication = createApplication(
                "AUTO-LIMIT-PAID-2", event.id(), secondPaid, "PAID", event.eventDate());

        scheduler.assignClosedEvents();

        List<Long> selectedAfterFirstRun = selectedStallIds(firstApplication, secondApplication);
        assertThat(selectedAfterFirstRun).hasSize(1);
        assertThat(eventState(event.id()))
                .containsEntry("workflowStatus", "PUBLISHED")
                .containsEntry("brandsPublicAt", null);

        scheduler.assignClosedEvents();

        List<Long> selectedAfterSecondRun = selectedStallIds(firstApplication, secondApplication);
        assertThat(selectedAfterSecondRun).containsExactlyElementsOf(selectedAfterFirstRun);
        assertThat(selectedAfterSecondRun).doesNotHaveDuplicates();
        assertThat(eventState(event.id()))
                .containsEntry("workflowStatus", "PUBLISHED")
                .containsEntry("brandsPublicAt", null);
    }

    private TestEvent createDueEvent(String key, LocalDateTime now, int stallCount) {
        Long organizerId = createUser("ORGANIZER", key.toLowerCase() + "-organizer@example.test");
        LocalDate eventDate = now.plusDays(10).toLocalDate();
        LocalDateTime startAt = eventDate.atTime(10, 0);
        Long eventId = jdbc.queryForObject("""
                INSERT INTO market_events (
                    user_id, title, summary, description, location_name, city, district, address,
                    start_at, end_at, registration_start_at, registration_end_at,
                    max_booths, base_fee, map_image_url, workflow_status
                )
                OUTPUT INSERTED.id
                VALUES (
                    :organizerId, :title, N'自動選位整合測試', N'自動選位整合測試',
                    N'測試場地', N'台北市', N'中正區', N'測試地址',
                    :startAt, :endAt, :registrationStartAt, :registrationEndAt,
                    :maxBooths, 1000, N'/images/auto-flow-map.jpg', N'PUBLISHED'
                )
                """, new MapSqlParameterSource()
                .addValue("organizerId", organizerId)
                .addValue("title", key + " 自動選位活動")
                .addValue("startAt", startAt)
                .addValue("endAt", startAt.plusHours(8))
                .addValue("registrationStartAt", now.minusDays(2))
                .addValue("registrationEndAt", now.minusMinutes(2))
                .addValue("maxBooths", stallCount), Long.class);

        Long categoryId = jdbc.queryForObject(
                "SELECT TOP 1 id FROM categories ORDER BY id", Map.of(), Long.class);
        jdbc.update("""
                INSERT INTO market_event_categories (event_id, category_id)
                VALUES (:eventId, :categoryId)
                """, Map.of("eventId", eventId, "categoryId", categoryId));
        Long zoneId = jdbc.queryForObject("""
                INSERT INTO event_stall_zones (event_id, zone_name, stall_count)
                OUTPUT INSERTED.id
                VALUES (:eventId, N'A 區', :stallCount)
                """, Map.of("eventId", eventId, "stallCount", stallCount), Long.class);
        for (int index = 1; index <= stallCount; index++) {
            jdbc.update("""
                    INSERT INTO event_stalls (event_id, zone_id, stall_no, status)
                    VALUES (:eventId, :zoneId, :stallNo, N'AVAILABLE')
                    """, new MapSqlParameterSource()
                    .addValue("eventId", eventId)
                    .addValue("zoneId", zoneId)
                    .addValue("stallNo", "A" + String.format("%02d", index)));
        }
        return new TestEvent(eventId, organizerId, eventDate);
    }

    private TestVendor createVendor(String email, String brandName) {
        Long userId = createUser("VENDOR", email);
        Long categoryId = jdbc.queryForObject(
                "SELECT TOP 1 id FROM categories ORDER BY id", Map.of(), Long.class);
        userRepository.createUserProfile(userId, "VENDOR", brandName, email);
        Long userProfileId = jdbc.queryForObject("""
                SELECT id FROM user_profiles
                WHERE user_id = :userId AND profile_type = N'VENDOR'
                """, Map.of("userId", userId), Long.class);
        jdbc.update("""
                INSERT INTO vendor_profiles (user_profile_id, category_id, brand_name)
                VALUES (:userProfileId, :categoryId, :brandName)
                """, new MapSqlParameterSource()
                .addValue("userProfileId", userProfileId)
                .addValue("categoryId", categoryId)
                .addValue("brandName", brandName));
        Long vendorProfileId = jdbc.queryForObject("""
                SELECT id FROM vendor_profiles WHERE user_profile_id = :userProfileId
                """, Map.of("userProfileId", userProfileId), Long.class);
        return new TestVendor(userId, vendorProfileId);
    }

    private Long createApplication(
            String applicationNo,
            Long eventId,
            TestVendor vendor,
            String paymentStatus,
            LocalDate eventDate) {
        Long applicationId = jdbc.queryForObject("""
                INSERT INTO event_applications (
                    application_no, event_id, user_id, vendor_profile_id,
                    total_amount, review_status, payment_status
                )
                OUTPUT INSERTED.id
                VALUES (
                    :applicationNo, :eventId, :userId, :vendorProfileId,
                    1000, N'APPROVED', :paymentStatus
                )
                """, new MapSqlParameterSource()
                .addValue("applicationNo", applicationNo)
                .addValue("eventId", eventId)
                .addValue("userId", vendor.userId())
                .addValue("vendorProfileId", vendor.vendorProfileId())
                .addValue("paymentStatus", paymentStatus), Long.class);
        jdbc.update("""
                INSERT INTO application_dates (application_id, apply_date)
                VALUES (:applicationId, :eventDate)
                """, Map.of("applicationId", applicationId, "eventDate", eventDate));
        return applicationId;
    }

    private Long createUser(String role, String email) {
        Long userId = userRepository.createLocalUser(role, email, "hash");
        userRepository.markEmailVerified(userId);
        return userId;
    }

    private boolean isCancelled(Long applicationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT is_cancelled FROM event_applications WHERE id = :id",
                Map.of("id", applicationId), Boolean.class));
    }

    private Map<Long, Long> selectedStalls(Long... applicationIds) {
        return jdbc.query("""
                SELECT application_id, selected_stall_id
                FROM application_dates
                WHERE application_id IN (:applicationIds)
                  AND selected_stall_id IS NOT NULL
                """, Map.of("applicationIds", List.of(applicationIds)), rs -> {
            Map<Long, Long> result = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                result.put(rs.getLong("application_id"), rs.getLong("selected_stall_id"));
            }
            return result;
        });
    }

    private List<Long> selectedStallIds(Long... applicationIds) {
        return selectedStalls(applicationIds).values().stream().toList();
    }

    private Map<String, Object> eventState(Long eventId) {
        return jdbc.queryForMap("""
                SELECT workflow_status AS workflowStatus,
                       brands_public_at AS brandsPublicAt
                FROM market_events
                WHERE id = :eventId
                """, Map.of("eventId", eventId));
    }

    private String stallNo(Long stallId) {
        return jdbc.queryForObject(
                "SELECT stall_no FROM event_stalls WHERE id = :stallId",
                Map.of("stallId", stallId), String.class);
    }

    private int notificationCount(Long eventId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM notifications notification
                INNER JOIN event_applications application
                    ON application.id = notification.target_id
                WHERE application.event_id = :eventId
                  AND notification.category = N'STALL_ASSIGNMENT'
                """, Map.of("eventId", eventId), Integer.class);
        return count == null ? 0 : count;
    }

    private record TestEvent(Long id, Long organizerId, LocalDate eventDate) {}

    private record TestVendor(Long userId, Long vendorProfileId) {}
}
