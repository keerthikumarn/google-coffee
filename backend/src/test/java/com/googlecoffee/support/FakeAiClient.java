package com.googlecoffee.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecoffee.port.AiClient;

import java.util.Optional;

/** AiClient with a programmed answer that records what it was asked, so no model is ever called. */
public class FakeAiClient implements AiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Optional<JsonNode> answer = Optional.empty();
    private String lastPrompt;
    private float lastTemperature;
    private int calls;

    /** The model answers with this JSON. */
    public FakeAiClient answering(String json) {
        try {
            answer = Optional.of(MAPPER.readTree(json));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("test JSON is invalid", e);
        }
        return this;
    }

    /** The model is slow, down or returned garbage: the contract says that means an empty result. */
    public FakeAiClient unavailable() {
        answer = Optional.empty();
        return this;
    }

    public String lastPrompt() {
        return lastPrompt;
    }

    public float lastTemperature() {
        return lastTemperature;
    }

    public int calls() {
        return calls;
    }

    @Override
    public Optional<JsonNode> generateJson(String prompt, float temperature) {
        calls++;
        lastPrompt = prompt;
        lastTemperature = temperature;
        return answer;
    }

    @Override
    public String displayName() {
        return "Fake AI";
    }
}
