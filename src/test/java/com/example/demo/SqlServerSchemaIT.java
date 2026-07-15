package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SqlServerSchemaIT extends SqlServerIntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

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

}
