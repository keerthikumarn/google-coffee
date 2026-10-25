package com.googlecoffee.support;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks tests that start a real PostgreSQL in Docker. Without Docker they are skipped with a loud warning, or
 * fail when {@code REQUIRE_DOCKER_TESTS=true} (CI sets it, so they can never be skipped there unnoticed).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DockerAvailableCondition.class)
public @interface RequiresDocker {}
