package com.googlecoffee.adapter.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecoffee.ai.JsonText;
import com.googlecoffee.config.AppProperties;
import com.googlecoffee.port.AiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Local LLM via Ollama's /api/chat with JSON mode. Same contract as the Gemini client:
 * returns Optional.empty() on any failure so the app falls back gracefully.
 */
@Component
@Profile("local")
public class OllamaClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final RestClient http;
    private final ObjectMapper mapper;
    private final AppProperties.Ollama cfg;

    public OllamaClient(RestClient.Builder builder, ObjectMapper mapper, AppProperties props) {
        this.cfg = props.ollama();
        if (cfg == null || cfg.baseUrl() == null || cfg.model() == null) {
            throw new IllegalStateException("app.ollama.base-url and app.ollama.model must be set for the 'local' profile");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(10, cfg.timeoutSeconds())));
        this.http = builder.baseUrl(cfg.baseUrl()).requestFactory(factory).build();
        this.mapper = mapper;
    }

    /** Friendly startup check so a missing model or stopped Ollama is obvious in the log. */
    @EventListener(ApplicationReadyEvent.class)
    public void checkModel() {
        try {
            JsonNode tags = http.get().uri("/api/tags").retrieve().body(JsonNode.class);
            boolean found = false;
            if (tags != null) {
                for (JsonNode m : tags.path("models")) {
                    String name = m.path("name").asText("");
                    if (name.equals(cfg.model()) || name.equals(cfg.model() + ":latest")) found = true;
                }
            }
            if (found) {
                log.info("Ollama ready at {} with model '{}'", cfg.baseUrl(), cfg.model());
            } else {
                log.warn("Ollama is running but model '{}' is not pulled. Run: ollama pull {}", cfg.model(), cfg.model());
            }
        } catch (Exception e) {
            log.warn("Ollama not reachable at {} ({}). Start it with `ollama serve`. AI features will use fallbacks.",
                    cfg.baseUrl(), e.getMessage());
        }
    }

    @Override
    public Optional<JsonNode> generateJson(String prompt, float temperature) {
        Map<String, Object> body = Map.of(
                "model", cfg.model(),
                "stream", false,
                "format", "json",
                "keep_alive", "30m",
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "options", Map.of("temperature", temperature, "num_ctx", 8192));
        long start = System.currentTimeMillis();
        try {
            JsonNode response = http.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String text = response == null ? null : response.path("message").path("content").asText(null);
            if (text == null || text.isBlank()) {
                log.warn("Ollama returned an empty response");
                return Optional.empty();
            }
            JsonNode node = mapper.readTree(JsonText.stripFences(text));
            log.debug("Ollama call took {} ms", System.currentTimeMillis() - start);
            return Optional.of(node);
        } catch (Exception e) {
            log.warn("Ollama call failed after {} ms: {}", System.currentTimeMillis() - start, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public String displayName() {
        return "Ollama (" + cfg.model() + ")";
    }
}
