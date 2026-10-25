package com.googlecoffee.adapter.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecoffee.support.RequiresDocker;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real PostgreSQL (same major version as docker-compose) with the production Flyway migrations applied.
 * One container is shared by every test class in the run. Without Docker these tests are skipped with a loud
 * warning, or fail when REQUIRE_DOCKER_TESTS=true (see {@link RequiresDocker}).
 */
@RequiresDocker
abstract class PostgresTestBase {

    private static PostgreSQLContainer postgres;
    static JdbcClient jdbc;
    static final JsonColumns JSON = new JsonColumns(new ObjectMapper());

    @BeforeAll
    static synchronized void startDatabase() {
        if (postgres == null) {
            postgres = new PostgreSQLContainer("postgres:17-alpine");
            postgres.start(); // stopped by Testcontainers when the JVM exits
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = JdbcClient.create(dataSource);
    }

    @BeforeEach
    void cleanTables() {
        jdbc.sql("TRUNCATE feedback, orders, sessions, menu_items, settings").update();
    }
}
