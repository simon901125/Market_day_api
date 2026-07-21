package com.example.demo.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AutomaticStallAssignmentRepository {

    public record AssignmentEvent(Long eventId, String eventTitle, Long organizerUserId) {}

    public record PendingDate(Long applicationDateId, Long applicationId, LocalDate applyDate) {}

    public record CompletedApplication(
            Long applicationId,
            Long vendorUserId,
            String eventTitle,
            String brandName) {}

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AutomaticStallAssignmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Long> findDueEventIds(LocalDateTime now) {
        String sql = """
                SELECT event.id
                FROM dbo.market_events event
                WHERE event.workflow_status = N'PUBLISHED'
                  AND CAST(event.registration_end_at AS date) < CAST(:now AS date)
                ORDER BY event.registration_end_at, event.id
                """;
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("now", now),
                (rs, rowNum) -> rs.getLong("id"));
    }

    public AssignmentEvent lockDueEvent(Long eventId, LocalDateTime now) {
        String sql = """
                SELECT event.id AS eventId,
                       event.title AS eventTitle,
                       event.user_id AS organizerUserId
                FROM dbo.market_events event WITH (UPDLOCK, HOLDLOCK, ROWLOCK)
                WHERE event.id = :eventId
                  AND event.workflow_status = N'PUBLISHED'
                  AND CAST(event.registration_end_at AS date) < CAST(:now AS date)
                """;
        List<AssignmentEvent> rows = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("eventId", eventId)
                        .addValue("now", now),
                (rs, rowNum) -> new AssignmentEvent(
                        rs.getLong("eventId"),
                        rs.getString("eventTitle"),
                        rs.getLong("organizerUserId")));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<PendingDate> findPendingDates(Long eventId) {
        String sql = """
                SELECT application_date.id AS applicationDateId,
                       application.id AS applicationId,
                       application_date.apply_date AS applyDate
                FROM dbo.event_applications application
                INNER JOIN dbo.application_dates application_date
                    ON application_date.application_id = application.id
                WHERE application.event_id = :eventId
                  AND application.review_status = N'APPROVED'
                  AND application.payment_status = N'PAID'
                  AND application.is_cancelled = 0
                  AND application_date.selected_stall_id IS NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM dbo.refunds refund
                      WHERE refund.application_id = application.id
                  )
                ORDER BY application_date.apply_date, application.id, application_date.id
                """;
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("eventId", eventId),
                (rs, rowNum) -> new PendingDate(
                        rs.getLong("applicationDateId"),
                        rs.getLong("applicationId"),
                        rs.getDate("applyDate").toLocalDate()));
    }

    public int cancelUnpaidApplications(Long eventId) {
        String sql = """
                UPDATE dbo.event_applications
                SET is_cancelled = 1
                WHERE event_id = :eventId
                  AND payment_status <> N'PAID'
                  AND is_cancelled = 0
                """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource("eventId", eventId));
    }

    public Long findFirstAvailableStall(Long eventId, LocalDate applyDate) {
        String sql = """
                SELECT TOP (1) stall.id
                FROM dbo.event_stalls stall WITH (UPDLOCK, READPAST, ROWLOCK)
                WHERE stall.event_id = :eventId
                  AND stall.status <> N'DISABLED'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM dbo.application_dates occupied
                      WHERE occupied.apply_date = :applyDate
                        AND occupied.selected_stall_id = stall.id
                  )
                ORDER BY stall.id
                """;
        List<Long> rows = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("eventId", eventId)
                        .addValue("applyDate", applyDate),
                (rs, rowNum) -> rs.getLong("id"));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public int assignStall(Long applicationDateId, LocalDate applyDate, Long stallId) {
        String sql = """
                UPDATE dbo.application_dates
                SET selected_stall_id = :stallId
                WHERE id = :applicationDateId
                  AND apply_date = :applyDate
                  AND selected_stall_id IS NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM dbo.application_dates occupied
                      WHERE occupied.apply_date = :applyDate
                        AND occupied.selected_stall_id = :stallId
                  )
                """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("applicationDateId", applicationDateId)
                .addValue("applyDate", applyDate)
                .addValue("stallId", stallId));
    }

    public int countIncompleteEligibleApplications(Long eventId) {
        String sql = """
                SELECT COUNT(*)
                FROM dbo.event_applications application
                WHERE application.event_id = :eventId
                  AND application.review_status = N'APPROVED'
                  AND application.payment_status = N'PAID'
                  AND application.is_cancelled = 0
                  AND NOT EXISTS (
                      SELECT 1
                      FROM dbo.refunds refund
                      WHERE refund.application_id = application.id
                  )
                  AND (
                      NOT EXISTS (
                          SELECT 1 FROM dbo.application_dates application_date
                          WHERE application_date.application_id = application.id
                      )
                      OR EXISTS (
                          SELECT 1 FROM dbo.application_dates application_date
                          WHERE application_date.application_id = application.id
                            AND application_date.selected_stall_id IS NULL
                      )
                  )
                """;
        Integer count = jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource("eventId", eventId),
                Integer.class);
        return count == null ? 0 : count;
    }

    public List<CompletedApplication> findCompletedApplications(
            Long eventId, List<Long> applicationIds) {
        if (applicationIds == null || applicationIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT application.id AS applicationId,
                       application.user_id AS vendorUserId,
                       event.title AS eventTitle,
                       vendor.brand_name AS brandName
                FROM dbo.event_applications application
                INNER JOIN dbo.market_events event ON event.id = application.event_id
                INNER JOIN dbo.vendor_profiles vendor ON vendor.id = application.vendor_profile_id
                WHERE application.event_id = :eventId
                  AND application.id IN (:applicationIds)
                  AND NOT EXISTS (
                      SELECT 1
                      FROM dbo.application_dates application_date
                      WHERE application_date.application_id = application.id
                        AND application_date.selected_stall_id IS NULL
                  )
                ORDER BY application.id
                """;
        return jdbcTemplate.query(
                sql,
                Map.of("eventId", eventId, "applicationIds", applicationIds),
                (rs, rowNum) -> new CompletedApplication(
                        rs.getLong("applicationId"),
                        rs.getLong("vendorUserId"),
                        rs.getString("eventTitle"),
                        rs.getString("brandName")));
    }

    public int finishFinalReview(Long eventId, LocalDateTime publishedAt) {
        String sql = """
                UPDATE dbo.market_events
                SET workflow_status = N'FINAL_REVIEW',
                    brands_public_at = COALESCE(brands_public_at, :publishedAt)
                WHERE id = :eventId
                  AND workflow_status = N'PUBLISHED'
                """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("publishedAt", publishedAt));
    }
}
