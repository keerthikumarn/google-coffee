package com.googlecoffee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String gcpProjectId,
        String firestoreDatabase,
        String geminiModel,
        String geminiLocation,
        int geminiTimeoutSeconds,
        String staffPin,
        String tokenSecret,
        int defaultBaristas,
        Ollama ollama) {

    /** Settings for the "local" profile. Null when running with the "gcp" profile. */
    public record Ollama(String baseUrl, String model, int timeoutSeconds) {}
}
