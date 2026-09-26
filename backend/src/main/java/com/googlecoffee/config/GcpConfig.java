package com.googlecoffee.config;

import com.google.cloud.ServiceOptions;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.genai.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Google Cloud clients. Credentials come from Application Default
 * Credentials: `gcloud auth application-default login` locally, the service
 * account on Cloud Run.
 */
@Configuration
public class GcpConfig {

    private static final Logger log = LoggerFactory.getLogger(GcpConfig.class);

    @Bean
    public String gcpProjectId(AppProperties props) {
        String id = props.gcpProjectId();
        if (id == null || id.isBlank()) {
            id = ServiceOptions.getDefaultProjectId();
        }
        if (id == null || id.isBlank()) {
            throw new IllegalStateException(
                    "GCP project not set. Export GCP_PROJECT_ID or run `gcloud config set project <id>`.");
        }
        log.info("Using GCP project '{}'", id);
        return id;
    }

    @Bean
    public Firestore firestore(String gcpProjectId, AppProperties props) {
        return FirestoreOptions.newBuilder()
                .setProjectId(gcpProjectId)
                .setDatabaseId(props.firestoreDatabase())
                .build()
                .getService();
    }

    @Bean
    public Client genAiClient(String gcpProjectId, AppProperties props) {
        log.info("Gemini model '{}' via Vertex AI in '{}'", props.geminiModel(), props.geminiLocation());
        return Client.builder()
                .vertexAI(true)
                .project(gcpProjectId)
                .location(props.geminiLocation())
                .build();
    }
}
