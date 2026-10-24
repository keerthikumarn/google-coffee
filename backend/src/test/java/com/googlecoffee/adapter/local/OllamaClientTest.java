package com.googlecoffee.adapter.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecoffee.config.AppProperties;
import com.sun.net.httpserver.HttpExchange;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Ollama client against a stand-in HTTP server: no model, no network, no install needed. */
class OllamaClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record Received(String method, String path, String contentType, String body) {}

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private HttpServer server;
    private final List<Received> requests = Collections.synchronizedList(new ArrayList<>());
    private int chatStatus = 200;
    private String chatBody = chatReply("{\"ok\":true}");
    private int tagsStatus = 200;
    private String tagsBody = "{\"models\":[{\"name\":\"llama3.2:latest\"}]}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            requests.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            respond(exchange, chatStatus, chatBody);
        });
        server.createContext("/api/tags", exchange -> {
            requests.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), null, ""));
            respond(exchange, tagsStatus, tagsBody);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /** What Ollama returns from /api/chat: the model's text sits in message.content. */
    private static String chatReply(String content) {
        try {
            return MAPPER.writeValueAsString(Map.of("message", Map.of("role", "assistant", "content", content)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static AppProperties props(AppProperties.Ollama ollama) {
        return new AppProperties("p", "(default)", "m", "l", 10, "0000", "secret", 1, ollama);
    }

    private OllamaClient client() {
        return new OllamaClient(RestClient.builder(), MAPPER, props(new AppProperties.Ollama(baseUrl(), "llama3.2", 10)), registry);
    }

    // ---------- generateJson ----------

    @Test
    void sendsOneNonStreamingJsonModeChatRequestWithThePrompt() throws Exception {
        client().generateJson("Pick a drink", 0.6f);

        assertEquals(1, requests.size());
        Received r = requests.get(0);
        assertEquals("POST", r.method());
        assertEquals("/api/chat", r.path());
        assertTrue(r.contentType().startsWith("application/json"));

        JsonNode body = MAPPER.readTree(r.body());
        assertEquals("llama3.2", body.path("model").asText());
        assertFalse(body.path("stream").asBoolean(true));
        assertEquals("json", body.path("format").asText());
        assertEquals("30m", body.path("keep_alive").asText());
        assertEquals(1, body.path("messages").size());
        assertEquals("user", body.path("messages").get(0).path("role").asText());
        assertEquals("Pick a drink", body.path("messages").get(0).path("content").asText());
        assertEquals(0.6, body.path("options").path("temperature").asDouble(), 0.0001);
        assertEquals(8192, body.path("options").path("num_ctx").asInt());
    }

    @Test
    void returnsTheJsonTheModelWrote() {
        chatBody = chatReply("{\"reply\":\"Try the flat white\",\"suggestions\":[]}");

        Optional<JsonNode> result = client().generateJson("hi", 0.2f);

        assertEquals("Try the flat white", result.orElseThrow().path("reply").asText());
    }

    @Test
    void understandsJsonWrappedInMarkdownFences() {
        chatBody = chatReply("```json\n{\"reply\":\"ok\"}\n```");

        assertEquals("ok", client().generateJson("hi", 0.2f).orElseThrow().path("reply").asText());
    }

    @Test
    void emptyOrMissingModelTextMeansNoAnswer() {
        chatBody = chatReply("   ");
        assertTrue(client().generateJson("hi", 0.2f).isEmpty());

        chatBody = "{}";
        assertTrue(client().generateJson("hi", 0.2f).isEmpty());

        chatBody = "{\"message\":{\"role\":\"assistant\"}}";
        assertTrue(client().generateJson("hi", 0.2f).isEmpty());
    }

    @Test
    void textThatIsNotJsonMeansNoAnswer() {
        chatBody = chatReply("Sorry, I can only chat in prose today.");

        assertTrue(client().generateJson("hi", 0.2f).isEmpty());
    }

    @Test
    void anHttpErrorMeansNoAnswerNotAnException() {
        chatStatus = 500;
        chatBody = "{\"error\":\"model crashed\"}";
        assertTrue(assertDoesNotThrow(() -> client().generateJson("hi", 0.2f)).isEmpty());

        chatStatus = 404;
        chatBody = "{\"error\":\"model 'llama3.2' not found\"}";
        assertTrue(assertDoesNotThrow(() -> client().generateJson("hi", 0.2f)).isEmpty());
    }

    @Test
    void anUnreachableOllamaMeansNoAnswerNotAnException() {
        OllamaClient unreachable = new OllamaClient(RestClient.builder(), MAPPER,
                props(new AppProperties.Ollama("http://127.0.0.1:1", "llama3.2", 10)), registry);

        assertTrue(assertDoesNotThrow(() -> unreachable.generateJson("hi", 0.2f)).isEmpty());
    }

    @Test
    void namesTheModelOnTheStaffDashboard() {
        assertEquals("Ollama (llama3.2)", client().displayName());
    }

    // ---------- metrics ----------

    private double requests(String outcome) {
        var counter = registry.find("ai.requests").tags("provider", "ollama", "outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    private long timedCalls() {
        var timer = registry.find("ai.request.duration").tag("provider", "ollama").timer();
        return timer == null ? 0 : timer.count();
    }

    @Test
    void aGoodAnswerIsCountedAsSuccessAndTimed() {
        client().generateJson("hi", 0.2f);

        assertEquals(1, requests("success"));
        assertEquals(0, requests("empty") + requests("error"));
        assertEquals(1, timedCalls());
    }

    @Test
    void anEmptyAnswerIsCountedAsEmpty() {
        chatBody = chatReply("   ");
        client().generateJson("hi", 0.2f);

        chatBody = "{}";
        client().generateJson("hi", 0.2f);

        assertEquals(2, requests("empty"));
        assertEquals(0, requests("success") + requests("error"));
    }

    @Test
    void textThatIsNotJsonAnHttpErrorAndAnUnreachableOllamaAreAllCountedAsErrors() {
        chatBody = chatReply("only prose");
        client().generateJson("hi", 0.2f);

        chatStatus = 500;
        client().generateJson("hi", 0.2f);

        new OllamaClient(RestClient.builder(), MAPPER,
                        props(new AppProperties.Ollama("http://127.0.0.1:1", "llama3.2", 10)), registry)
                .generateJson("hi", 0.2f);

        assertEquals(3, requests("error"));
        assertEquals(0, requests("success") + requests("empty"));
    }

    @Test
    void everyCallIsTimedWhateverItsOutcome() {
        client().generateJson("hi", 0.2f);
        chatBody = chatReply("");
        client().generateJson("hi", 0.2f);
        chatStatus = 500;
        client().generateJson("hi", 0.2f);

        assertEquals(3, timedCalls());
    }

    @Test
    void theStartupCheckIsNotCountedAsAnAiRequest() {
        client().checkModel();

        assertEquals(0, requests("success") + requests("empty") + requests("error"));
        assertEquals(0, timedCalls());
    }

    // ---------- configuration ----------

    @Test
    void refusesToStartWithoutOllamaSettings() {
        for (AppProperties.Ollama bad : new AppProperties.Ollama[] {
                null,
                new AppProperties.Ollama(null, "llama3.2", 10),
                new AppProperties.Ollama(baseUrl(), null, 10)}) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new OllamaClient(RestClient.builder(), MAPPER, props(bad), registry));
            assertTrue(e.getMessage().contains("app.ollama.base-url"));
        }
    }

    // ---------- startup check ----------

    @Test
    void startupCheckAsksOllamaWhichModelsItHas() {
        client().checkModel();

        assertEquals(1, requests.size());
        assertEquals("GET", requests.get(0).method());
        assertEquals("/api/tags", requests.get(0).path());
    }

    @Test
    void startupCheckNeverFailsTheAppWhateverOllamaSays() {
        OllamaClient client = client();

        assertDoesNotThrow(client::checkModel);                       // model present (":latest")

        tagsBody = "{\"models\":[{\"name\":\"mistral\"}]}";
        assertDoesNotThrow(client::checkModel);                       // model not pulled

        tagsBody = "{}";
        assertDoesNotThrow(client::checkModel);                       // no model list at all

        tagsBody = "not json at all";
        assertDoesNotThrow(client::checkModel);                       // garbage

        tagsStatus = 500;
        assertDoesNotThrow(client::checkModel);                       // server error

        OllamaClient unreachable = new OllamaClient(RestClient.builder(), MAPPER,
                props(new AppProperties.Ollama("http://127.0.0.1:1", "llama3.2", 10)), registry);
        assertDoesNotThrow(unreachable::checkModel);                  // Ollama not running
    }
}
