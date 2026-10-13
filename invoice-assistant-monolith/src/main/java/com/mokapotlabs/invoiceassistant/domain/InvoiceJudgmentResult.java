package com.mokapotlabs.invoiceassistant.domain;

import java.util.Objects;
import java.util.UUID;
import org.pipelineframework.connector.decision.DecisionResult;

public record InvoiceJudgmentResult(UUID documentId, TextAnalysisResult context, DecisionResult decisionResult) {
    public InvoiceJudgmentResult {
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(context);
        Objects.requireNonNull(decisionResult);
        if (!documentId.equals(context.documentId()))
            throw new IllegalArgumentException("Decision result must belong to its invoice.");
    }
}
