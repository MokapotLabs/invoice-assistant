package org.pipelineframework.connector.decision.jev;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.pipelineframework.connector.decision.*;

/** Application integration contract against the pinned, unmodified provider client. */
class OllamaDecisionProtocolTest {
    @Test
    void pinnedProviderUsesNativeSystemoneAndDecodesBoundedChoices() throws Exception {
        var observed = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/systemone", exchange -> {
            observed.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] reply =
                    """
    {"model":"tev1","answers":{"property":{"type":"choice","choice":"maple","confidence":0.98,"probabilities":{"maple":0.99,"oak":0.01}}},"usage":{"input_tokens":100,"output_tokens":0}}
    """
                            .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();
        try (var http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()) {
            var client = new JevDecisionClient(
                    AuthenticatedJevConnection.bearer(http, "ollama"),
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "tev1",
                    Duration.ofSeconds(3));
            var response = client.decide(new DecisionRequest(
                            "{\"invoiceText\":\"17 Maple Street\"}",
                            List.of(new DecisionQuestion(
                                    "property",
                                    DecisionQuestionType.CHOICE,
                                    "Select service address",
                                    List.of(
                                            new DecisionCriterion("maple", "17 Maple Street"),
                                            new DecisionCriterion("oak", "4 Oak Avenue"))))))
                    .toCompletableFuture()
                    .join();
            assertEquals("maple", response.result().answers().getFirst().selected());
            var body = new ObjectMapper().readTree(observed.get());
            assertEquals("tev1", body.path("model").asText());
            assertEquals("choice", body.at("/questions/property/type").asText());
            assertTrue(body.at("/questions/property/criteria").has("maple"));
            assertFalse(body.has("messages"));
        } finally {
            server.stop(0);
        }
    }
}
