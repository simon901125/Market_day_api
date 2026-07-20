package com.example.demo.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LifecycleNotificationRepository {

    public record ExpiredApplication(Long applicationId, Long vendorUserId, String eventTitle) {}

    public record EndedEventRecipient(Long eventId, Long vendorUserId, String eventTitle) {}

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public LifecycleNotificationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ExpiredApplication> expirePendingApplications(LocalDateTime now) {
        String sql = """
                UPDATE application
                SET payment_status = N'EXPIRED'
                OUTPUT inserted.id AS applicationId,
                       inserted.user_id AS vendorUserId,
                       event.title AS eventTitle
                FROM dbo.event_applications application WITH (UPDLOCK, READPAST, ROWLOCK)
                INNER JOIN dbo.market_events event ON event.id = application.event_id
                WHERE application.payment_status = N'PENDING'
                  AND application.review_status = N'APPROVED'
                  AND application.is_cancelled = 0
                  AND application.payment_due_at IS NOT NULL
                  AND application.payment_due_at < :now
                  AND NOT EXISTS (
                      SELECT 1
                      FROM dbo.refunds refund
                      WHERE refund.application_id = application.id
                  )
                """;
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("now", now),
                (rs, rowNum) -> new ExpiredApplication(
                        rs.getLong("applicationId"),
                        rs.getLong("vendorUserId"),
                        rs.getString("eventTitle")));
    }

    public void expirePendingPaymentRecords(List<Long> applicationIds) {
        if (applicationIds == null || applicationIds.isEmpty()) {
            return;
        }
        String sql = """
                UPDATE dbo.payments
                SET status = N'EXPIRED'
                WHERE application_id IN (:applicationIds)
                  AND status = N'PENDING'
                """;
        jdbcTemplate.update(sql, Map.of("applicationIds", applicationIds));
    }

    public List<EndedEventRecipient> findEndedEventRecipients(LocalDateTime now) {
        String sql = """
                SELECT DISTINCT
                    event.id AS eventId,
                    application.user_id AS vendorUserId,
                    event.title AS eventTitle
                FROM dbo.market_events event
                INNER JOIN dbo.event_applications application ON application.event_id = event.id
                WHERE event.workflow_status IN (N'PUBLISHED', N'FINAL_REVIEW')
                  AND event.end_at < :now
                  AND application.is_cancelled = 0
                  AND application.review_status = N'APPROVED'
                  AND application.payment_status = N'PAID'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM dbo.refunds refund
                      WHERE refund.application_id = application.id
                        AND refund.refund_status IN (N'REFUND_REQUESTED', N'REFUNDING', N'REFUNDED')
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM dbo.notifications notification
                      WHERE notification.user_id = application.user_id
                        AND notification.type = N'EVENT_ENDED'
                        AND notification.target_type = N'MARKET_EVENT'
                        AND notification.target_id = event.id
                  )
                ORDER BY event.id, application.user_id
                """;
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("now", now),
                (rs, rowNum) -> new EndedEventRecipient(
                        rs.getLong("eventId"),
                        rs.getLong("vendorUserId"),
                        rs.getString("eventTitle")));
    }
}
