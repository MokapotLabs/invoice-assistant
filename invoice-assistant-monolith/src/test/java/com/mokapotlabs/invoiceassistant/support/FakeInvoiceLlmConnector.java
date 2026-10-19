package com.mokapotlabs.invoiceassistant.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.pipelineframework.connector.ConnectorRuntimeContext;
import org.pipelineframework.connector.llm.LlmDecision;
import org.pipelineframework.connector.llm.LlmDecisionClient;
import org.pipelineframework.connector.llm.LlmDecisionClientResolver;
import org.pipelineframework.connector.llm.LlmProviderConfiguration;
import org.pipelineframework.connector.llm.LlmQueryConnectorProvider;
import org.pipelineframework.connector.llm.LlmToolProposal;
import org.pipelineframework.connector.llm.LlmTurnRequest;

@ApplicationScoped
public final class FakeInvoiceLlmConnector extends LlmQueryConnectorProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final AtomicInteger TEXT_CALLS = new AtomicInteger();
    private static final AtomicInteger VISION_CALLS = new AtomicInteger();
    private static final AtomicReference<LlmTurnRequest> LAST_REQUEST = new AtomicReference<>();
    private static final java.util.Set<String> FAILED_ONCE = ConcurrentHashMap.newKeySet();

    public FakeInvoiceLlmConnector() {
        super(org.pipelineframework.connector.ConnectorProviderId.of("llm.query.openai.compatible"));
    }

    public static int calls() {
        return CALLS.get();
    }

    public static LlmTurnRequest lastRequest() {
        return LAST_REQUEST.get();
    }

    public static int textCalls() {
        return TEXT_CALLS.get();
    }

    public static int visionCalls() {
        return VISION_CALLS.get();
    }

    @Override
    protected LlmDecisionClientResolver createClientResolver(
            LlmProviderConfiguration configuration, ConnectorRuntimeContext context) {
        return ignored -> CompletableFuture.completedStage(new LlmDecisionClient() {
            @Override
            public boolean supportsNativeStructuredOutput() {
                return true;
            }

            @Override
            public CompletionStage<LlmDecision> decide(LlmTurnRequest request) {
                LAST_REQUEST.set(request);
                CALLS.incrementAndGet();
                try {
                    JsonNode state = MAPPER.readTree(request.applicationStateJson());
                    String originalFilename =
                            state.path("evidence").path("originalFilename").asText();
                    boolean vision = state.has("textReview");
                    if (vision) VISION_CALLS.incrementAndGet();
                    else TEXT_CALLS.incrementAndGet();
                    if (originalFilename.startsWith("analysis-fail-once") && FAILED_ONCE.add(originalFilename)) {
                        return CompletableFuture.failedFuture(new IllegalStateException("Fixture analysis failed"));
                    }
                    ObjectNode completion = MAPPER.createObjectNode();
                    boolean requiresVision = originalFilename.contains("vision-required")
                            || originalFilename.contains("vision-ambiguous");
                    boolean ambiguous = originalFilename.contains("vision-ambiguous");
                    if (vision) {
                        completion.put("supplier", ambiguous ? "Unknown supplier" : "Visual fixture supplier");
                        completion.put("supplierEvidenceStatus", ambiguous ? "INSUFFICIENT" : "STRONG_VISUAL_IDENTITY");
                    } else {
                        ObjectNode facts = completion.putObject("facts");
                        facts.put("supplier", requiresVision ? "Unknown supplier" : "Fixture supplier");
                        facts.put("supplierEvidenceStatus", requiresVision ? "INSUFFICIENT" : "EXPLICIT_TEXT");
                        facts.put("invoiceNumber", "FIXTURE-1001");
                        facts.put("totalAmount", 1059.38);
                        facts.put(
                                "diagnostic",
                                originalFilename.startsWith("auto-clear")
                                        ? "No extraction warnings."
                                        : "Fixture one-turn structured completion");
                        ObjectNode recommendation = completion.putObject("recommendation");
                        recommendation.put("propertyId", "maple-street");
                        recommendation.put("explanation", "The invoice address matches the configured property alias.");
                        completion.put("note", "Friday callout beside the coastal service address.");
                    }
                    return CompletableFuture.completedFuture(
                            new LlmDecision(new LlmToolProposal("complete", MAPPER.writeValueAsString(completion))));
                } catch (Exception failure) {
                    return CompletableFuture.failedFuture(failure);
                }
            }
        });
    }
}
