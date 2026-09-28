package com.googlecoffee.port;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * Any LLM that can answer a prompt with a JSON object.
 * Implementations: Gemini on Vertex AI (profile "gcp"), Ollama (profile "local").
 * Must never throw: return Optional.empty() on any failure so callers can fall back.
 */
public interface AiClient {
 
    Optional<JsonNode> generateJson(String prompt, float temperature);
 
    /** Human-readable provider name, shown on the staff dashboard. */
    String displayName();
}
