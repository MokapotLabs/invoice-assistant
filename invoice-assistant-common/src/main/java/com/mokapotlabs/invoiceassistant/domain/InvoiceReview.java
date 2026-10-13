package com.mokapotlabs.invoiceassistant.domain;

import java.util.Objects;

public record InvoiceReview(InvoiceFacts facts, PropertyRecommendation recommendation, String note) {
    public InvoiceReview(InvoiceFacts facts, PropertyRecommendation recommendation) {
        this(facts, recommendation, "Invoice review imported from earlier analysis.");
    }

    public InvoiceReview {
        facts = Objects.requireNonNull(facts, "review facts must not be null");
        recommendation = Objects.requireNonNull(recommendation, "review recommendation must not be null");
        if (note == null || note.isBlank()) {
            note = "Invoice review imported from earlier analysis.";
        }
        note = note.trim();
    }
}
