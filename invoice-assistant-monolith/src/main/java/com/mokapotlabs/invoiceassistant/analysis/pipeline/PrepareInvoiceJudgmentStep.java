package com.mokapotlabs.invoiceassistant.analysis.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mokapotlabs.invoiceassistant.domain.*;
import io.quarkus.arc.Unremovable;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import org.pipelineframework.connector.decision.*;
import org.pipelineframework.service.ReactiveService;

@ApplicationScoped
@Unremovable
public class PrepareInvoiceJudgmentStep implements ReactiveService<TextAnalysisResult, InvoiceJudgmentRequest> {
    @Override
    public Uni<InvoiceJudgmentRequest> process(TextAnalysisResult result) {
        return Uni.createFrom().item(() -> prepare(result));
    }

    InvoiceJudgmentRequest prepare(TextAnalysisResult result) {
        try {
            String state = new ObjectMapper()
                    .writeValueAsString(Map.of(
                            "invoiceText",
                            result.evidence().text(),
                            "facts",
                            result.review().facts(),
                            "properties",
                            result.properties(),
                            "originalFilename",
                            result.evidence().originalFilename()));
            var questions = List.of(
                    new DecisionQuestion(
                            "supplierEvidence",
                            DecisionQuestionType.CHOICE,
                            "Judge only textual evidence identifying the invoice issuer, not customer or recipient identity.",
                            List.of(
                                    new DecisionCriterion("EXPLICIT_TEXT", "Issuer directly labelled or named."),
                                    new DecisionCriterion(
                                            "STRONG_TEXTUAL_IDENTITY",
                                            "Several consistent textual cues identify the issuer."),
                                    new DecisionCriterion(
                                            "INSUFFICIENT", "Text absent, conflicting, generic or ambiguous."))),
                    new DecisionQuestion(
                            "property",
                            DecisionQuestionType.CHOICE,
                            "Choose the configured property supported by the invoice service address.",
                            result.properties().stream()
                                    .map(property -> new DecisionCriterion(
                                            property.id(),
                                            property.canonicalAddress() + "; aliases: "
                                                    + String.join(", ", property.aliases())))
                                    .toList()));
            return new InvoiceJudgmentRequest(result.documentId(), result, new DecisionRequest(state, questions));
        } catch (Exception failure) {
            throw new IllegalArgumentException("Invoice decision state cannot be prepared.", failure);
        }
    }
}
