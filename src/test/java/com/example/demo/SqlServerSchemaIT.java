package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SqlServerSchemaIT extends SqlServerIntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void connectsOnlyToDedicatedSqlServerTestDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();

            assertThat(metadata.getDatabaseProductName())
                    .containsIgnoringCase("Microsoft SQL Server");
            assertThat(connection.getCatalog())
                    .isEqualToIgnoringCase(TEST_DATABASE_NAME);
        }
    }

    @Test
    void notificationsTableProvidesMinimumInAppNotificationFields() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT c.name
                FROM sys.columns c
                WHERE c.object_id = OBJECT_ID(N'dbo.notifications')
                ORDER BY c.column_id
                """, String.class);

        assertThat(columns).containsExactly(
                "id",
                "user_id",
                "category",
                "type",
                "target_type",
                "target_id",
                "dedup_key",
                "title",
                "content",
                "is_read",
                "read_at",
                "created_at");

        List<String> checkConstraints = jdbcTemplate.queryForList("""
                SELECT cc.name
                FROM sys.check_constraints cc
                WHERE cc.parent_object_id = OBJECT_ID(N'dbo.notifications')
                """, String.class);

        assertThat(checkConstraints).contains(
                "CK_notifications_category",
                "CK_notifications_target_type",
                "CK_notifications_target_reference",
                "CK_notifications_read_state");

        Integer dedupIndexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys.indexes
                WHERE object_id = OBJECT_ID(N'dbo.notifications')
                  AND name = N'UX_notifications_dedup_key'
                  AND is_unique = 1
                  AND has_filter = 1
                """, Integer.class);
        assertThat(dedupIndexCount).isEqualTo(1);
    }

    @Test
    void notificationTargetTypeAcceptsEventUnpublishRequest() {
        jdbcTemplate.update("""
                INSERT INTO dbo.users (role, email, password_hash, provider)
                VALUES (N'ADMIN', N'notification-target-it@example.test', N'test', N'LOCAL')
                """);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM dbo.users WHERE email = N'notification-target-it@example.test'",
                Long.class);

        int inserted = jdbcTemplate.update("""
                INSERT INTO dbo.notifications (
                    user_id, category, type, target_type, target_id, title, content
                ) VALUES (?, N'EVENT_CHANGE', N'EVENT_UNPUBLISH_REQUEST_REVISION_REQUIRED',
                          N'EVENT_UNPUBLISH_REQUEST', 99, N'下架申請需補件', N'測試通知')
                """, userId);

        assertThat(inserted).isEqualTo(1);
    }

}
