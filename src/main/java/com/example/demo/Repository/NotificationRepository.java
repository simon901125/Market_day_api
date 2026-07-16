package com.example.demo.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import com.example.demo.dto.notification.NotificationCreateCommand;
import com.example.demo.dto.response.VendorNotificationItemResponse;
import com.example.demo.enums.notification.NotificationCategory;

@Repository
public class NotificationRepository {

    private static final String INSERT_SQL = """
            INSERT INTO dbo.notifications (
                user_id,
                category,
                type,
                target_type,
                target_id,
                title,
                content
            )
            VALUES (
                :userId,
                :category,
                :type,
                :targetType,
                :targetId,
                :title,
                :content
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public NotificationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int create(NotificationCreateCommand command) {
        return jdbcTemplate.update(INSERT_SQL, parameters(command));
    }

    public int[] createAll(Collection<NotificationCreateCommand> commands) {
        SqlParameterSource[] batch = commands.stream()
                .map(this::parameters)
                .toArray(SqlParameterSource[]::new);
        return jdbcTemplate.batchUpdate(INSERT_SQL, batch);
    }

    public List<VendorNotificationItemResponse> findVendorNotifications(
            Long userId,
            NotificationCategory category,
            boolean unreadOnly,
            LocalDateTime retentionStart,
            int offset,
            int pageSize) {
        String sql = """
                SELECT
                    id,
                    category,
                    type,
                    target_type,
                    target_id,
                    title,
                    content,
                    is_read,
                    read_at,
                    created_at
                FROM dbo.notifications
                WHERE user_id = :userId
                  AND created_at >= :retentionStart
                  AND (:category IS NULL OR category = :category)
                  AND (:unreadOnly = 0 OR is_read = 0)
                ORDER BY is_read ASC, created_at DESC, id DESC
                OFFSET :offset ROWS FETCH NEXT :pageSize ROWS ONLY
                """;

        return jdbcTemplate.query(sql, queryParameters(
                userId, category, unreadOnly, retentionStart, offset, pageSize),
                (resultSet, rowNumber) -> new VendorNotificationItemResponse(
                        resultSet.getLong("id"),
                        resultSet.getString("category"),
                        resultSet.getString("type"),
                        resultSet.getString("target_type"),
                        nullableLong(resultSet.getObject("target_id")),
                        resultSet.getString("title"),
                        resultSet.getString("content"),
                        resultSet.getBoolean("is_read"),
                        nullableDateTime(resultSet.getTimestamp("read_at")),
                        nullableDateTime(resultSet.getTimestamp("created_at"))));
    }

    public long countVendorNotifications(
            Long userId,
            NotificationCategory category,
            boolean unreadOnly,
            LocalDateTime retentionStart) {
        String sql = """
                SELECT COUNT_BIG(*)
                FROM dbo.notifications
                WHERE user_id = :userId
                  AND created_at >= :retentionStart
                  AND (:category IS NULL OR category = :category)
                  AND (:unreadOnly = 0 OR is_read = 0)
                """;
        Long count = jdbcTemplate.queryForObject(
                sql,
                queryParameters(userId, category, unreadOnly, retentionStart, 0, 1),
                Long.class);
        return count == null ? 0 : count;
    }

    private MapSqlParameterSource queryParameters(
            Long userId,
            NotificationCategory category,
            boolean unreadOnly,
            LocalDateTime retentionStart,
            int offset,
            int pageSize) {
        return new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("category", category == null ? null : category.name())
                .addValue("unreadOnly", unreadOnly ? 1 : 0)
                .addValue("retentionStart", retentionStart)
                .addValue("offset", offset)
                .addValue("pageSize", pageSize);
    }

    private Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private LocalDateTime nullableDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private MapSqlParameterSource parameters(NotificationCreateCommand command) {
        return new MapSqlParameterSource()
                .addValue("userId", command.userId())
                .addValue("category", command.category().name())
                .addValue("type", command.type().name())
                .addValue("targetType", command.targetType().name())
                .addValue("targetId", command.targetId())
                .addValue("title", command.title())
                .addValue("content", command.content());
    }
}
