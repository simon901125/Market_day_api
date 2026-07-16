package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.List;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.demo.Repository.NotificationRepository;
import com.example.demo.dto.notification.NotificationCreateCommand;
import com.example.demo.enums.notification.NotificationCategory;
import com.example.demo.enums.notification.NotificationTargetType;
import com.example.demo.enums.notification.NotificationType;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NotificationRepositoryIT extends SqlServerIntegrationTestSupport {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsUnreadNotificationWithDatabaseTimestamp() {
        jdbcTemplate.update("""
                INSERT INTO dbo.users (role, email, password_hash, provider)
                VALUES (N'VENDOR', N'notification-it@example.test', N'test', N'LOCAL')
                """);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM dbo.users WHERE email = N'notification-it@example.test'",
                Long.class);

        int inserted = notificationRepository.create(new NotificationCreateCommand(
                userId,
                NotificationCategory.APPLICATION_REVIEW,
                NotificationType.APPLICATION_APPROVED,
                NotificationTargetType.EVENT_APPLICATION,
                99L,
                "報名審核通過",
                "整合測試市集審核通過"));

        Map<String, Object> notification = jdbcTemplate.queryForMap("""
                SELECT category, type, target_type, target_id, is_read, read_at, created_at
                FROM dbo.notifications
                WHERE user_id = ?
                """, userId);

        assertThat(inserted).isEqualTo(1);
        assertThat(notification)
                .containsEntry("category", "APPLICATION_REVIEW")
                .containsEntry("type", "APPLICATION_APPROVED")
                .containsEntry("target_type", "EVENT_APPLICATION")
                .containsEntry("target_id", 99L)
                .containsEntry("is_read", false)
                .containsEntry("read_at", null);
        assertThat(notification.get("created_at")).isNotNull();
    }

    @Test
    void batchCreatesTargetlessSystemNotificationsForEachRecipient() {
        jdbcTemplate.update("""
                INSERT INTO dbo.users (role, email, password_hash, provider)
                VALUES
                    (N'ADMIN', N'notification-admin-it@example.test', N'test', N'LOCAL'),
                    (N'ORGANIZER', N'notification-organizer-it@example.test', N'test', N'LOCAL')
                """);
        List<Long> userIds = jdbcTemplate.queryForList("""
                SELECT id
                FROM dbo.users
                WHERE email IN (
                    N'notification-admin-it@example.test',
                    N'notification-organizer-it@example.test'
                )
                ORDER BY id
                """, Long.class);

        int[] inserted = notificationRepository.createAll(userIds.stream()
                .map(userId -> new NotificationCreateCommand(
                        userId,
                        NotificationCategory.SYSTEM,
                        NotificationType.SYSTEM_ANNOUNCEMENT,
                        NotificationTargetType.SYSTEM,
                        null,
                        "系統公告",
                        "系統將進行維護"))
                .toList());

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM dbo.notifications
                WHERE user_id IN (?, ?)
                  AND category = N'SYSTEM'
                  AND target_type = N'SYSTEM'
                  AND target_id IS NULL
                """, Integer.class, userIds.get(0), userIds.get(1));

        assertThat(inserted).containsExactly(1, 1);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void vendorQueryFiltersOneYearAndCategoryAndSortsUnreadBeforeRead() {
        jdbcTemplate.update("""
                INSERT INTO dbo.users (role, email, password_hash, provider)
                VALUES (N'VENDOR', N'notification-query-it@example.test', N'test', N'LOCAL')
                """);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM dbo.users WHERE email = N'notification-query-it@example.test'",
                Long.class);

        long unreadApplicationId = insertNotification(
                userId, NotificationCategory.APPLICATION_REVIEW,
                NotificationType.APPLICATION_SUBMITTED, "unread-application");
        long unreadPaymentId = insertNotification(
                userId, NotificationCategory.PAYMENT,
                NotificationType.PAYMENT_PAID, "unread-payment");
        long readId = insertNotification(
                userId, NotificationCategory.EVENT_CHANGE,
                NotificationType.EVENT_UPDATED, "read-newer");
        long expiredId = insertNotification(
                userId, NotificationCategory.PAYMENT,
                NotificationType.PAYMENT_FAILED, "expired");

        jdbcTemplate.update("""
                UPDATE dbo.notifications
                SET created_at = '2026-07-01T10:00:00'
                WHERE id IN (?, ?)
                """, unreadApplicationId, unreadPaymentId);
        jdbcTemplate.update("""
                UPDATE dbo.notifications
                SET is_read = 1, read_at = '2026-07-02T10:00:00', created_at = '2026-07-02T10:00:00'
                WHERE id = ?
                """, readId);
        jdbcTemplate.update("""
                UPDATE dbo.notifications
                SET created_at = '2024-01-01T10:00:00'
                WHERE id = ?
                """, expiredId);

        LocalDateTime retentionStart = LocalDateTime.of(2025, 7, 15, 0, 0);
        var all = notificationRepository.findVendorNotifications(
                userId, null, false, retentionStart, 0, 10);
        var payment = notificationRepository.findVendorNotifications(
                userId, NotificationCategory.PAYMENT, false, retentionStart, 0, 10);
        var secondRow = notificationRepository.findVendorNotifications(
                userId, null, false, retentionStart, 1, 1);

        assertThat(all)
                .extracting(notification -> notification.id())
                .containsExactly(unreadPaymentId, unreadApplicationId, readId);
        assertThat(payment)
                .extracting(notification -> notification.id())
                .containsExactly(unreadPaymentId);
        assertThat(secondRow)
                .extracting(notification -> notification.id())
                .containsExactly(unreadApplicationId);
        assertThat(notificationRepository.countVendorNotifications(
                userId, null, true, retentionStart)).isEqualTo(2);
        assertThat(notificationRepository.countVendorNotifications(
                userId, null, false, retentionStart)).isEqualTo(3);
    }

    private long insertNotification(
            Long userId,
            NotificationCategory category,
            NotificationType type,
            String title) {
        notificationRepository.create(new NotificationCreateCommand(
                userId,
                category,
                type,
                NotificationTargetType.EVENT_APPLICATION,
                99L,
                title,
                title + " content"));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM dbo.notifications WHERE user_id = ? AND title = ?",
                Long.class,
                userId,
                title);
    }
}
