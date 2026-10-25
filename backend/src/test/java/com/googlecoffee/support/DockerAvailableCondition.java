package com.googlecoffee.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opentest4j.AssertionFailedError;
import org.testcontainers.DockerClientFactory;

/** Decides, once per run, whether tests marked {@link RequiresDocker} run, are skipped, or fail the build. */
public class DockerAvailableCondition implements ExecutionCondition {

    static final String REQUIRE_ENV = "REQUIRE_DOCKER_TESTS";

    private static volatile Boolean dockerAvailable;
    private static volatile boolean warned;

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return evaluate(isDockerAvailable(), "true".equalsIgnoreCase(System.getenv(REQUIRE_ENV)));
    }

    /** Pure decision, separated from the Docker probe and the environment so it can be tested. */
    static ConditionEvaluationResult evaluate(boolean dockerAvailable, boolean required) {
        if (dockerAvailable) {
            return ConditionEvaluationResult.enabled("Docker is available");
        }
        if (required) {
            throw new AssertionFailedError(
                    "Docker is required (" + REQUIRE_ENV + "=true) for the PostgreSQL adapter and whole-application "
                            + "tests, but it was not found. Start Docker (Docker Desktop, Colima or the docker daemon) "
                            + "and re-run.");
        }
        warnOnce();
        return ConditionEvaluationResult.disabled(
                "Docker was not found, so this test did not run (set " + REQUIRE_ENV + "=true to make that a failure)");
    }

    private static boolean isDockerAvailable() {
        if (dockerAvailable == null) {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        }
        return dockerAvailable;
    }

    private static synchronized void warnOnce() {
        if (warned) return;
        warned = true;
        System.err.println("""

                ================================================================================
                 DOCKER NOT FOUND: the PostgreSQL adapter tests and the whole-application
                 startup tests were SKIPPED. They did not run, so they have not passed.
                 Start Docker and re-run to include them, or set REQUIRE_DOCKER_TESTS=true
                 to make a missing Docker a failure (CI does this).
                ================================================================================
                """);
    }
}
