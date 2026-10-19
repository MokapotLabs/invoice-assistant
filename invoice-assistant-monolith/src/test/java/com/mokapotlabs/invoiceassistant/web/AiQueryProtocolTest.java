package com.mokapotlabs.invoiceassistant.web;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mokapotlabs.assistantlocal.connection.AiConnectionResolver;
import com.mokapotlabs.invoiceassistant.domain.VisionSupplierObservation;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.pipelineframework.connector.*;
import org.pipelineframework.connector.llm.*;
import org.pipelineframework.connector.llm.langchain4j.LangChain4jOpenAiCompatibleQueryConnector;
import org.pipelineframework.repository.PayloadReference;

/** Real pinned Query operation and SDK against a loopback OpenAI-compatible HTTP fixture. */
@QuarkusTest
@ResourceLock("invoice-assistant-data-root")
class AiQueryProtocolTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void bothModesSendStructuredTextAndImagesWithTheCorrectCredentials() throws Exception {
        var body = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String content = JSON.writeValueAsString(
                    Map.of("supplier", "Target", "supplierEvidenceStatus", "STRONG_VISUAL_IDENTITY"));
            String chunk = JSON.writeValueAsString(Map.of(
                    "id",
                    "fixture",
                    "object",
                    "chat.completion.chunk",
                    "created",
                    1,
                    "model",
                    "fixture",
                    "choices",
                    List.of(Map.of("index", 0, "delta", Map.of("role", "assistant", "content", content)))));
            String finish = JSON.writeValueAsString(Map.of(
                    "id",
                    "fixture",
                    "object",
                    "chat.completion.chunk",
                    "created",
                    1,
                    "model",
                    "fixture",
                    "choices",
                    List.of(Map.of("index", 0, "delta", Map.of(), "finish_reason", "stop"))));
            byte[] reply = ("data: " + chunk + "\n\ndata: " + finish + "\n\ndata: [DONE]\n\n")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();
        try {
            var resolver = new AiConnectionResolver(() -> Optional.of(" fixture-remote-key "));
            var invocationContext = new ConnectorExecutionContext(
                    Optional.of("local-invoice-assistant"),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());
            var runtime =
                    ConnectorRuntimeContext.of("fixture", Runnable::run, Clock.systemUTC(), Optional.of(resolver));
            for (boolean remote : List.of(false, true)) {
                for (boolean vision : List.of(false, true)) {
                    String model =
                            remote ? "google/gemini-3.1-flash-lite" : vision ? "qwen3-vl:4b-instruct" : "qwen3.5:4b";
                    var connector = new LangChain4jOpenAiCompatibleQueryConnector();
                    connector
                            .start(
                                    runtime,
                                    new LlmProviderConfiguration(
                                            model,
                                            Optional.of("http://127.0.0.1:"
                                                    + server.getAddress().getPort() + "/v1"),
                                            Optional.of(new ConnectionRef(remote ? "openrouter-primary" : "ollama"))))
                            .toCompletableFuture()
                            .get(10, TimeUnit.SECONDS);
                    @SuppressWarnings("unchecked")
                    var operation = (QueryOperation<Object, LlmTurnConfiguration, VisionSupplierObservation>)
                            connector.operations().iterator().next();
                    var input = new Evidence("Synthetic invoice", vision ? List.of(image().reference()) : List.of());
                    var outcome = operation
                            .query(new QueryInvocation<>(
                                    input,
                                    new LlmTurnConfiguration("Identify the supplier.", Map.of()),
                                    VisionSupplierObservation.class,
                                    invocationContext,
                                    Optional.of((reference, maxBytes) -> CompletableFuture.completedFuture(image()))))
                            .toCompletableFuture()
                            .get(10, TimeUnit.SECONDS);
                    var found = assertInstanceOf(QueryOutcome.Found.class, outcome);
                    assertEquals("Target", ((VisionSupplierObservation) found.output()).supplier());
                    assertEquals(remote ? "Bearer fixture-remote-key" : "Bearer ollama", authorization.get());
                    var request = JSON.readTree(body.get());
                    assertEquals(model, request.path("model").asText());
                    assertEquals(
                            "json_schema", request.at("/response_format/type").asText());
                    assertTrue(request.at("/response_format/json_schema/strict").asBoolean());
                    assertEquals(vision, body.get().contains("data:image/png;base64,"));
                    if (remote) assertFalse(request.has("reasoning_effort"));
                    else assertEquals("none", request.path("reasoning_effort").asText());
                    assertFalse(body.get().contains("fixture-remote-key"));
                    connector.stop(runtime).toCompletableFuture().join();
                }
            }
        } finally {
            server.stop(0);
        }
    }

    public record Evidence(String invoiceText, List<PayloadReference> images) {}

    private static MaterializedPayload image() {
        byte[] bytes = java.util.Base64.getDecoder()
                .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/Y9sAAAAASUVORK5CYII=");
        var reference = new PayloadReference(
                "fixture",
                "images",
                "logo.png",
                "image/png",
                "identity",
                "fixture-checksum",
                bytes.length,
                "1",
                Map.of(),
                Optional.empty());
        return new MaterializedPayload(reference, bytes, "image/png", "identity", "fixture-checksum");
    }
}
