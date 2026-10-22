package com.googlecoffee.adapter.local;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The database tests must never pass silently when Docker is missing. */
class PostgresTestBaseTest {

    @Test
    void missingDockerFailsWithInstructions() {
        AssertionFailedError e = assertThrows(AssertionFailedError.class, () -> PostgresTestBase.requireDocker(false));

        assertTrue(e.getMessage().contains("Docker is required"));
        assertTrue(e.getMessage().contains("-Dtest='!Pg*Test'"), "tells how to run without Docker");
    }

    @Test
    void availableDockerPasses() {
        assertDoesNotThrow(() -> PostgresTestBase.requireDocker(true));
    }
}
