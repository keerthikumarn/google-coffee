package com.googlecoffee.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.googlecoffee.config.AppProperties;
import com.googlecoffee.port.AiClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper over Gemini (Vertex AI). Always asks for JSON, enforces a
 * timeout and returns Optional.empty() on any failure so callers can fall
 * back gracefully instead of breaking the guest experience.
 */
@Service
@Profile("gcp")
public class GeminiService implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final Client client;
    private final ObjectMapper mapper;
    private final AppProperties props;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public GeminiService(Client client, ObjectMapper mapper, AppProperties props) {
        this.client = client;
        this.mapper = mapper;
        this.props = props;
    }

    @Override
    public Optional<JsonNode> generateJson(String prompt, float temperature) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .temperature(temperature)
                .maxOutputTokens(8192)
                .build();
        long start = System.currentTimeMillis();
        try {
            GenerateContentResponse response = CompletableFuture
                    .supplyAsync(() -> client.models.generateContent(props.geminiModel(), prompt, config), executor)
                    .get(props.geminiTimeoutSeconds(), TimeUnit.SECONDS);
            String text = response.text();
            if (text == null || text.isBlank()) {
                log.warn("Gemini returned an empty response");
                return Optional.empty();
            }
            JsonNode node = mapper.readTree(stripFences(text));
            log.debug("Gemini call took {} ms", System.currentTimeMillis() - start);
            return Optional.of(node);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Gemini call failed after {} ms: {}", System.currentTimeMillis() - start, e.toString());
            return Optional.empty();
        }
    }

    static String stripFences(String text) {
        return JsonText.stripFences(text);
    }

    @Override
    public String displayName() {
        return "Gemini (" + props.geminiModel() + ")";
    }

    @PreDestroy
    void stop() {
        executor.shutdown();
    }
}