package com.mokapotlabs.invoiceassistant.analysis.pipeline;

import static org.junit.jupiter.api.Assertions.*;

import com.mokapotlabs.invoiceassistant.domain.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.pipelineframework.connector.decision.*;
import org.pipelineframework.repository.PayloadReference;

class InvoiceJudgmentStepTest {
    private final PrepareInvoiceJudgmentStep prepare = new PrepareInvoiceJudgmentStep();
    private final ApplyInvoiceJudgmentStep apply = new ApplyInvoiceJudgmentStep();

    private TextAnalysisResult context() {
        var ref = new PayloadReference(
                "filesystem",
                "invoices",
                "invoice.pdf",
                "application/pdf",
                "raw",
                "hash",
                12,
                "v1",
                Map.of(),
                Optional.empty());
        var facts = new InvoiceFacts(
                "Example supplier",
                SupplierEvidenceStatus.EXPLICIT_TEXT,
                "INV-1",
                new BigDecimal("12.34"),
                "No extraction warnings.");
        return new TextAnalysisResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ref,
                "fixture",
                "_",
                "catalogue",
                new InvoiceEvidence(
                        "Example supplier INV-1 Total 12.34 17 Maple Street",
                        "invoice.pdf",
                        "evidence",
                        "Text extracted."),
                List.of(new Property("maple", "Maple", "17 Maple Street", List.of(), "17")),
                new InvoiceReview(facts, new PropertyRecommendation("maple", "Address match")));
    }

    private DecisionAnswer answer(String name, String selected, String confidence) {
        return new DecisionAnswer(
                name,
                DecisionQuestionType.CHOICE,
                selected,
                BigDecimal.ZERO,
                new BigDecimal(confidence),
                List.of(new DecisionProbability(selected, BigDecimal.ONE)));
    }

    private InvoiceJudgmentResult result(
            TextAnalysisResult context, String property, String supplier, String confidence) {
        return new InvoiceJudgmentResult(
                context.documentId(),
                context,
                new DecisionResult(List.of(
                        answer("supplierEvidence", supplier, confidence), answer("property", property, "0.99"))));
    }

    @Test
    void preparesBoundedQuestionsWithoutAuthorizationOrPayloads() {
        var input = context();
        var request = prepare.prepare(input);
        assertEquals(input.documentId(), request.documentId());
        assertSame(input, request.context());
        assertEquals(
                List.of("supplierEvidence", "property"),
                request.decision().questions().stream()
                        .map(DecisionQuestion::name)
                        .toList());
        assertFalse(request.decision().stateJson().contains("approvalPolicy"));
        assertFalse(request.decision().stateJson().contains("payloadReference"));
        assertEquals(
                List.of("maple", PrepareInvoiceJudgmentStep.NO_PROPERTY),
                request.decision().questions().getLast().criteria().stream()
                        .map(DecisionCriterion::label)
                        .toList());
    }

    @Test
    void lowConfidenceCannotEraseReviewWarningOrChangeCarriedEvidence() {
        var input = context();
        var output = apply.apply(result(input, "maple", "EXPLICIT_TEXT", "0.89"));
        assertTrue(output.review().facts().diagnostic().contains("human review required"));
        assertEquals(input.documentId(), output.documentId());
        assertEquals(input.reviewId(), output.reviewId());
        assertSame(input.evidence(), output.evidence());
        assertSame(input.invoice(), output.invoice());
    }

    @Test
    void rejectsUnconfiguredLabelsVisualTextEvidenceAndMismatchedIdentity() {
        var input = context();
        assertThrows(
                IllegalArgumentException.class, () -> apply.apply(result(input, "invented", "EXPLICIT_TEXT", "0.99")));
        assertThrows(
                IllegalArgumentException.class,
                () -> apply.apply(result(input, "maple", "STRONG_VISUAL_IDENTITY", "0.99")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new InvoiceJudgmentResult(
                        UUID.randomUUID(),
                        input,
                        result(input, "maple", "EXPLICIT_TEXT", "0.99").decisionResult()));
    }
 @Test void noSupportedPropertyWaitsForReviewEvenWhenTheChoiceIsConfident(){
  var input=context();var output=apply.apply(result(input,PrepareInvoiceJudgmentStep.NO_PROPERTY,"EXPLICIT_TEXT","0.99"));
  assertTrue(output.review().facts().diagnostic().contains("human review required"));
  assertEquals(input.review().recommendation().propertyId(),output.review().recommendation().propertyId());
 }
}
