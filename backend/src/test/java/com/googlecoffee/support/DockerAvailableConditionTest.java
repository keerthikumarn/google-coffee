package com.googlecoffee.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.opentest4j.AssertionFailedError;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Docker tests may be skipped on a machine without Docker, but never silently where they are required. */
class DockerAvailableConditionTest {

    @Test
    void withDockerTheTestsRun() {
        assertFalse(DockerAvailableCondition.evaluate(true, false).isDisabled());
        assertFalse(DockerAvailableCondition.evaluate(true, true).isDisabled());
    }

    @Test
    void withoutDockerTheTestsAreSkippedAndTheReasonSaysSo() {
        ConditionEvaluationResult result = DockerAvailableCondition.evaluate(false, false);

        assertTrue(result.isDisabled());
        String reason = result.getReason().orElseThrow();
        assertTrue(reason.contains("Docker was not found"));
        assertTrue(reason.contains("did not run"));
        assertTrue(reason.contains("REQUIRE_DOCKER_TESTS=true"));
    }

    @Test
    void withoutDockerWhereItIsRequiredTheBuildFailsWithInstructions() {
        AssertionFailedError failure =
                assertThrows(AssertionFailedError.class, () -> DockerAvailableCondition.evaluate(false, true));

        assertTrue(failure.getMessage().contains("REQUIRE_DOCKER_TESTS=true"));
        assertTrue(failure.getMessage().contains("Start Docker"));
    }

    @Test
    void theEnvironmentVariableThatMakesDockerRequiredIsNamedConsistently() {
        assertTrue(DockerAvailableCondition.REQUIRE_ENV.equals("REQUIRE_DOCKER_TESTS"));
    }
}
