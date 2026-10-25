package com.googlecoffee.support;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** A PostgreSQL container (same major version as docker-compose) started once for tests that boot the whole app. */
public final class SharedPostgres {

    private static PostgreSQLContainer postgres;

    private SharedPostgres() {}

    /** Callers are marked {@link RequiresDocker}, so Docker is known to be there by the time this runs. */
    public static synchronized PostgreSQLContainer start() {
        if (postgres == null) {
            postgres = new PostgreSQLContainer("postgres:17-alpine");
            postgres.start(); // stopped by Testcontainers when the JVM exits
        }
        return postgres;
    }
}
