package com.mokapotlabs.invoiceassistant.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.pipelineframework.connector.ConnectorProviderId;
import org.pipelineframework.connector.ConnectorRuntimeContext;
import org.pipelineframework.connector.decision.DecisionAnswer;
import org.pipelineframework.connector.decision.DecisionClientResolver;
import org.pipelineframework.connector.decision.DecisionProbability;
import org.pipelineframework.connector.decision.DecisionProviderConfiguration;
import org.pipelineframework.connector.decision.DecisionQueryConnectorProvider;
import org.pipelineframework.connector.decision.DecisionRequest;
import org.pipelineframework.connector.decision.DecisionResponse;
import org.pipelineframework.connector.decision.DecisionResult;

@ApplicationScoped
public final class FakeInvoiceDecisionConnector extends DecisionQueryConnectorProvider {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final AtomicReference<DecisionRequest> LAST_REQUEST = new AtomicReference<>();

    public FakeInvoiceDecisionConnector() {
        super(ConnectorProviderId.of("decision.query.jev"));
    }

    public static int calls() {
        return CALLS.get();
    }

    public static DecisionRequest lastRequest() {
        return LAST_REQUEST.get();
    }

    @Override
    protected DecisionClientResolver createClientResolver(
            DecisionProviderConfiguration configuration, ConnectorRuntimeContext context) {
        return ignored -> CompletableFuture.completedStage(request -> {
            LAST_REQUEST.set(request);
            CALLS.incrementAndGet();
            try {
                JsonNode state = JSON.readTree(request.stateJson());
                boolean requiresVision = state.path("originalFilename").asText().contains("vision-");
                List<DecisionAnswer> answers = request.questions().stream()
                        .map(question -> {
                            String selected = question.name().equals("supplierEvidence")
                                    ? (requiresVision ? "INSUFFICIENT" : "EXPLICIT_TEXT")
                                    : question.criteria().getFirst().label();
                            List<DecisionProbability> probabilities = probabilities(
                                    question.criteria().stream()
                                            .map(criterion -> criterion.label())
                                            .toList(),
                                    selected);
                            return new DecisionAnswer(
                                    question.name(),
                                    question.type(),
                                    selected,
                                    BigDecimal.ZERO,
                                    new BigDecimal("0.90"),
                                    probabilities);
                        })
                        .toList();
                return CompletableFuture.completedStage(
                        new DecisionResponse(new DecisionResult(answers), Optional.empty()));
            } catch (Exception failure) {
                return CompletableFuture.<DecisionResponse>failedStage(failure);
            }
        });
    }

    private static List<DecisionProbability> probabilities(List<String> labels, String selected) {
        if (labels.size() == 1) {
            return List.of(new DecisionProbability(selected, BigDecimal.ONE));
        }
        BigDecimal remainder = new BigDecimal("0.10");
        BigDecimal other = remainder.divide(BigDecimal.valueOf(labels.size() - 1), 12, java.math.RoundingMode.DOWN);
        List<DecisionProbability> probabilities = new ArrayList<>();
        int remaining = labels.size() - 1;
        for (String label : labels) {
            if (label.equals(selected)) probabilities.add(new DecisionProbability(label, new BigDecimal("0.90")));
            else {
                BigDecimal probability = --remaining == 0 ? remainder : other;
                probabilities.add(new DecisionProbability(label, probability));
                remainder = remainder.subtract(probability);
            }
        }
        return List.copyOf(probabilities);
    }
}
