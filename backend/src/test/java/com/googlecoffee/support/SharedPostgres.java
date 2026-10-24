package com.googlecoffee.support;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.fail;

/** A PostgreSQL container (same major version as docker-compose) started once for tests that boot the whole app. */
public final class SharedPostgres {

    private static PostgreSQLContainer postgres;

    private SharedPostgres() {}

    /** Docker is required: without it the test fails with instructions instead of silently skipping. */
    public static synchronized PostgreSQLContainer start() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            fail("Docker is required for tests that start the whole application but was not found. "
                    + "Start Docker (Docker Desktop, Colima, or the docker daemon) and re-run.");
        }
        if (postgres == null) {
            postgres = new PostgreSQLContainer("postgres:17-alpine");
            postgres.start(); // stopped by Testcontainers when the JVM exits
        }
        return postgres;
    }
}
