package com.googlecoffee.adapter.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Real PostgreSQL (same major version as docker-compose) with the production Flyway migrations applied.
 * One container is shared by every test class in the run. Docker is required: without it these tests
 * fail with instructions instead of being skipped, so a green build always means they ran.
 */
abstract class PostgresTestBase {

    private static PostgreSQLContainer postgres;
    static JdbcClient jdbc;
    static final JsonColumns JSON = new JsonColumns(new ObjectMapper());

    @BeforeAll
    static synchronized void startDatabase() {
        requireDocker(DockerClientFactory.instance().isDockerAvailable());
        if (postgres == null) {
            postgres = new PostgreSQLContainer("postgres:17-alpine");
            postgres.start(); // stopped by Testcontainers when the JVM exits
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = JdbcClient.create(dataSource);
    }

    static void requireDocker(boolean dockerAvailable) {
        if (!dockerAvailable) {
            fail("Docker is required for the PostgreSQL adapter tests but was not found. "
                    + "Start Docker (Docker Desktop, Colima, or the docker daemon) and re-run, "
                    + "or run only the Docker-free tests with: ./mvnw test -Dtest='!Pg*Test'");
        }
    }

    @BeforeEach
    void cleanTables() {
        jdbc.sql("TRUNCATE feedback, orders, sessions, menu_items, settings").update();
    }
}
