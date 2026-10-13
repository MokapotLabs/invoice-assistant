package com.mokapotlabs.invoiceassistant.domain;

import java.util.Objects;
import java.util.UUID;
import org.pipelineframework.connector.decision.DecisionRequest;

public record InvoiceJudgmentRequest(UUID documentId, TextAnalysisResult context, DecisionRequest decision) {
    public InvoiceJudgmentRequest {
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(context);
        Objects.requireNonNull(decision);
        if (!documentId.equals(context.documentId()))
            throw new IllegalArgumentException("Decision request must belong to its invoice.");
    }
}
