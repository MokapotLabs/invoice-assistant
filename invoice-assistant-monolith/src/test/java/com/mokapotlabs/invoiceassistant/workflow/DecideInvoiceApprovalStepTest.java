package com.mokapotlabs.invoiceassistant.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.mokapotlabs.invoiceassistant.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.pipelineframework.repository.PayloadReference;

class DecideInvoiceApprovalStepTest {
    private final DecideInvoiceApprovalStep step = new DecideInvoiceApprovalStep();
    private static final Property PROPERTY = new Property("chosen", "Chosen", "456 Main Street", List.of(), "456");

    @Test
    void approvesLiteralEvidenceWithCapturedDeliverySettings() {
        ReviewReady input = review(
                "Supplier INV-1 Total 12.34 Service address 456 Main Street",
                "No extraction warnings.",
                "chosen",
                true,
                true,
                List.of(PROPERTY));
        var result = assertInstanceOf(CompletedConfirmation.class, step.decide(input));
        assertEquals("chosen", result.confirmedInvoice().confirmedPropertyId());
        assertEquals("accountant@example.test", result.confirmedInvoice().emailRecipient());
        assertEquals("Invoice chosen", result.confirmedInvoice().emailSubject());
        assertSame(input, result.review());
    }

    @Test
    void uncertaintyAndMissingAuthorityAlwaysGoToReview() {
        for (ReviewReady input : List.of(
                review(
                        "Supplier INV-1 12.34 456 Main Street",
                        "Possible duplicate total.",
                        "chosen",
                        true,
                        true,
                        List.of(PROPERTY)),
                review("Supplier INV-1 12.34", "No extraction warnings.", "chosen", true, true, List.of(PROPERTY)),
                review(
                        "Supplier INV-1 12.34 456 Main Street",
                        "No extraction warnings.",
                        "other",
                        true,
                        true,
                        List.of(PROPERTY)),
                review(
                        "Supplier INV-1 12.34 456 Main Street",
                        "No extraction warnings.",
                        "chosen",
                        false,
                        true,
                        List.of(PROPERTY)),
                review(
                        "Supplier INV-1 12.34 456 Main Street",
                        "No extraction warnings.",
                        "chosen",
                        true,
                        false,
                        List.of(PROPERTY)),
                review(
                        "Supplier INV-1 12.34 456 Main Street and 789 North Street",
                        "No extraction warnings.",
                        "chosen",
                        true,
                        true,
                        List.of(PROPERTY, new Property("other", "Other", "789 North Street", List.of(), "789"))),
                review(
                        "Supplier INV-2 12.34 456 Main Street",
                        "No extraction warnings.",
                        "chosen",
                        true,
                        true,
                        List.of(PROPERTY)),
                review(
                        "Supplier INV-1 1234 456 Main Street",
                        "No extraction warnings.",
                        "chosen",
                        true,
                        true,
                        List.of(PROPERTY)))) {
            assertSame(input, step.decide(input));
        }
    }

    private static ReviewReady review(
            String text,
            String diagnostic,
            String recommendation,
            boolean enabled,
            boolean ready,
            List<Property> properties) {
        var reference = new PayloadReference(
                "filesystem",
                "invoices",
                "invoice.pdf",
                "application/pdf",
                "raw",
                "sha256",
                12,
                "v1",
                Map.of(),
                Optional.empty());
        var policy = new AutoApprovalPolicy(
                enabled,
                Instant.parse("2026-10-12T10:00:00Z"),
                List.of(new AutomaticDelivery("chosen", "accountant@example.test", ready)),
                "Invoice {property}",
                "{supplier} {filename}");
        var facts = new InvoiceFacts(
                "Supplier", SupplierEvidenceStatus.EXPLICIT_TEXT, "INV-1", new BigDecimal("12.34"), diagnostic);
        return new ReviewReady(
                UUID.randomUUID(),
                UUID.randomUUID(),
                reference,
                "invoice.pdf",
                "evidence",
                "catalogue",
                "v1",
                "_",
                properties,
                new InvoiceReview(facts, new PropertyRecommendation(recommendation, "Address match")),
                Optional.of(new InvoiceEvidence(text, "invoice.pdf", "hash", "Text extracted.", policy)));
    }
}
