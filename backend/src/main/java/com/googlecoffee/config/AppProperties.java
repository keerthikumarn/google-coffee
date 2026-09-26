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
        int defaultBaristas) {
}
