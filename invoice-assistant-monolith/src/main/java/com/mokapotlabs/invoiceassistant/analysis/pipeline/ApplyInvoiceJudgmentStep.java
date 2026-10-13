package com.mokapotlabs.invoiceassistant.analysis.pipeline;

import com.mokapotlabs.invoiceassistant.domain.*;
import io.quarkus.arc.Unremovable;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.pipelineframework.connector.decision.*;
import org.pipelineframework.service.ReactiveService;

@ApplicationScoped
@Unremovable
public class ApplyInvoiceJudgmentStep implements ReactiveService<InvoiceJudgmentResult, TextAnalysisResult> {
    @Override
    public Uni<TextAnalysisResult> process(InvoiceJudgmentResult result) {
        return Uni.createFrom().item(() -> apply(result));
    }

    TextAnalysisResult apply(InvoiceJudgmentResult result) {
        var answers = result.decisionResult().answers().stream()
                .collect(Collectors.toUnmodifiableMap(DecisionAnswer::name, Function.identity()));
        if (answers.size() != 2)
            throw new IllegalArgumentException("Expected exactly the authored decision questions.");
        var supplier = choice(answers, "supplierEvidence");
        var property = choice(answers, "property");
        SupplierEvidenceStatus status = SupplierEvidenceStatus.valueOf(supplier.selected());
        if (status == SupplierEvidenceStatus.STRONG_VISUAL_IDENTITY)
            throw new IllegalArgumentException("Text decision cannot choose visual evidence.");
        var context = result.context();
        boolean unresolved = property.selected().equals(PrepareInvoiceJudgmentStep.NO_PROPERTY);
        if (!unresolved && context.properties().stream().noneMatch(value -> value.id().equals(property.selected())))
            throw new IllegalArgumentException("Decision selected an unknown property.");
        var old = context.review().facts();
        // Low-confidence choices remain visible for review and cannot pass the approval policy.
        String diagnostic = unresolved || supplier.confidence().compareTo(new BigDecimal("0.90")) < 0
                        || property.confidence().compareTo(new BigDecimal("0.90")) < 0
                ? "Local bounded decision is uncertain; human review required."
                : old.diagnostic();
        var review = new InvoiceReview(
                new InvoiceFacts(old.supplier(), status, old.invoiceNumber(), old.totalAmount(), diagnostic),
                new PropertyRecommendation(unresolved ? context.review().recommendation().propertyId() : property.selected(),
                        unresolved ? "No configured property is supported confidently. Check the invoice service address."
                            : "Check that the suggested property matches the service address on the invoice."),
                context.review().note());
        return new TextAnalysisResult(
                context.documentId(),
                context.reviewId(),
                context.invoice(),
                context.analysisProfile(),
                context.filenameSeparator(),
                context.catalogueHash(),
                context.evidence(),
                context.properties(),
                review);
    }

    private static DecisionAnswer choice(Map<String, DecisionAnswer> values, String key) {
        var answer = java.util.Optional.ofNullable(values.get(key))
                .orElseThrow(() -> new IllegalArgumentException("Missing decision answer: " + key));
        if (answer.type() != DecisionQuestionType.CHOICE)
            throw new IllegalArgumentException("Wrong decision answer type: " + key);
        return answer;
    }
}
