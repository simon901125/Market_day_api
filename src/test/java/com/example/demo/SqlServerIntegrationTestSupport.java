package com.example.demo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Shared SQL Server integration-test setup.
 *
 * <p>It allows tests to connect only to {@code MarketDayDB_Test}, rebuilds the
 * test schema once per test JVM, and supplies the same datasource properties to
 * every integration-test class that extends this support class.</p>
 */
@ActiveProfiles("integration")
abstract class SqlServerIntegrationTestSupport {

    protected static final String TEST_DATABASE_NAME = "MarketDayDB_Test";

    private static final String DEFAULT_TEST_DB_URL =
            "jdbc:sqlserver://localhost:1433;databaseName=" + TEST_DATABASE_NAME
                    + ";encrypt=true;trustServerCertificate=true";
    private static final String SCHEMA_RESOURCE = "/sql/MarketDayDBIntegration.sql";
    private static final Pattern DATABASE_NAME_PATTERN = Pattern.compile(
            "(?i)(?:databaseName|database)\\s*=\\s*([^;]+)");
    private static final Pattern GO_BATCH_SEPARATOR = Pattern.compile(
            "(?im)^\\s*GO\\s*$");
    private static final Object DATABASE_INITIALIZATION_LOCK = new Object();

    private static volatile boolean databaseInitialized;

    @DynamicPropertySource
    static void configureDedicatedTestDatabase(DynamicPropertyRegistry registry) {
        String databaseUrl = environmentValue("TEST_DB_URL", DEFAULT_TEST_DB_URL);
        String username = environmentValue("TEST_DB_USERNAME", "sa");
        String password = environmentValue("TEST_DB_PASSWORD", "");

        assertDedicatedTestDatabase(databaseUrl);
        initializeDatabaseOnce(databaseUrl, username, password);

        registry.add("spring.datasource.url", () -> databaseUrl);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
    }

    private static void initializeDatabaseOnce(
            String databaseUrl,
            String username,
            String password) {
        if (databaseInitialized) {
            return;
        }

        synchronized (DATABASE_INITIALIZATION_LOCK) {
            if (databaseInitialized) {
                return;
            }
            rebuildDedicatedTestDatabase(databaseUrl, username, password);
            databaseInitialized = true;
        }
    }

    private static void assertDedicatedTestDatabase(String databaseUrl) {
        Matcher matcher = DATABASE_NAME_PATTERN.matcher(databaseUrl);
        if (!matcher.find()) {
            throw new IllegalStateException(
                    "TEST_DB_URL must explicitly specify databaseName=" + TEST_DATABASE_NAME);
        }

        String databaseName = matcher.group(1).trim();
        if (!TEST_DATABASE_NAME.toLowerCase(Locale.ROOT)
                .equals(databaseName.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "SQL integration tests may only use database " + TEST_DATABASE_NAME
                            + ", but TEST_DB_URL points to " + databaseName);
        }
    }

    private static void rebuildDedicatedTestDatabase(
            String databaseUrl,
            String username,
            String password) {
        String masterUrl = DATABASE_NAME_PATTERN.matcher(databaseUrl)
                .replaceFirst("databaseName=master");
        String[] batches = GO_BATCH_SEPARATOR.split(readSchemaSql());

        try (Connection connection = DriverManager.getConnection(masterUrl, username, password);
                Statement statement = connection.createStatement()) {
            for (int index = 0; index < batches.length; index++) {
                String batch = batches[index].trim();
                if (batch.isEmpty()) {
                    continue;
                }

                try {
                    statement.execute(batch);
                } catch (SQLException exception) {
                    throw new IllegalStateException(
                            "Failed to execute integration schema batch " + (index + 1),
                            exception);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to rebuild dedicated integration database " + TEST_DATABASE_NAME,
                    exception);
        }
    }

    private static String readSchemaSql() {
        try (InputStream inputStream =
                SqlServerIntegrationTestSupport.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException(
                        "Missing integration schema resource " + SCHEMA_RESOURCE);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to read integration schema resource " + SCHEMA_RESOURCE,
                    exception);
        }
    }

    private static String environmentValue(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
