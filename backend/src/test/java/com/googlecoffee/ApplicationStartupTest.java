package com.googlecoffee;

import com.googlecoffee.support.RequiresDocker;
import com.googlecoffee.support.SharedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole application (local profile, real PostgreSQL) starts, and its operational endpoints are locked down.
 * Spring Boot turns metric export off in tests by default, hence {@code @AutoConfigureObservability}.
 */
@RequiresDocker
@AutoConfigureObservability
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0"})
class ApplicationStartupTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgreSQLContainer postgres = SharedPostgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort int port;
    @LocalManagementPort int managementPort;
    @Autowired TestRestTemplate http;

    private String app(String path) {
        return "http://localhost:" + port + path;
    }

    private String ops(String path) {
        return "http://localhost:" + managementPort + path;
    }

    @Test
    void theApplicationStartsAndServesItsApi() {
        ResponseEntity<String> health = http.getForEntity(app("/api/health"), String.class);

        assertEquals(HttpStatus.OK, health.getStatusCode());
        assertTrue(health.getBody().contains("\"status\":\"ok\""));
    }

    @Test
    void theMenuIsSeededOnFirstStart() {
        ResponseEntity<String> menu = http.getForEntity(app("/api/menu"), String.class);

        assertEquals(HttpStatus.OK, menu.getStatusCode());
        assertTrue(menu.getBody().contains("\"id\":\"espresso\""));
    }

    @Test
    void operationsRunOnTheirOwnPortNotTheMainOne() {
        assertTrue(managementPort != port, "management must not share the public port");

        assertEquals(HttpStatus.NOT_FOUND, http.getForEntity(app("/actuator/prometheus"), String.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, http.getForEntity(app("/actuator/health"), String.class).getStatusCode());
    }

    @Test
    void healthReportsUpWithTheDatabaseConnected() {
        ResponseEntity<String> health = http.getForEntity(ops("/actuator/health"), String.class);

        assertEquals(HttpStatus.OK, health.getStatusCode());
        assertTrue(health.getBody().contains("\"status\":\"UP\""));
    }

    @Test
    void prometheusServesJvmPoolAndRequestMetrics() {
        http.getForEntity(app("/api/menu"), String.class); // make sure at least one request has been timed

        ResponseEntity<String> scrape = http.getForEntity(ops("/actuator/prometheus"), String.class);

        assertEquals(HttpStatus.OK, scrape.getStatusCode());
        String body = scrape.getBody();
        assertTrue(body.contains("jvm_memory_used_bytes"));
        assertTrue(body.contains("hikaricp_connections"));
        assertTrue(body.contains("http_server_requests_seconds_count{") && body.contains("uri=\"/api/menu\""));
    }

    @Test
    void onlyHealthAndPrometheusAreExposed() {
        String discovery = http.getForEntity(ops("/actuator"), String.class).getBody();

        assertTrue(discovery.contains("\"health\""));
        assertTrue(discovery.contains("\"prometheus\""));
        for (String sensitive : new String[] {"env", "beans", "configprops", "heapdump", "threaddump", "loggers", "mappings"}) {
            assertFalse(discovery.contains("\"" + sensitive + "\""), sensitive + " must not be exposed");
            assertEquals(HttpStatus.NOT_FOUND, http.getForEntity(ops("/actuator/" + sensitive), String.class).getStatusCode());
        }
    }
}
