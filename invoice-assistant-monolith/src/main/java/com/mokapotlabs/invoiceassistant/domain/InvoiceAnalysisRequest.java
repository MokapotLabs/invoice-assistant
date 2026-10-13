package com.mokapotlabs.invoiceassistant.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.pipelineframework.repository.PayloadReference;

/** Complete semantic input to the one-turn invoice analysis Query. */
public record InvoiceAnalysisRequest(
        UUID documentId,
        UUID reviewId,
        String invoiceSourceIdentity,
        String catalogueSourceIdentity,
        PayloadReference invoice,
        String analysisProfile,
        String filenameSeparator,
        String catalogueHash,
        InvoiceEvidence evidence,
        List<Property> properties) {
    public InvoiceAnalysisRequest {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(reviewId, "reviewId must not be null");
        invoiceSourceIdentity = required(invoiceSourceIdentity, "invoiceSourceIdentity");
        catalogueSourceIdentity = required(catalogueSourceIdentity, "catalogueSourceIdentity");
        Objects.requireNonNull(invoice, "invoice must not be null");
        analysisProfile = required(analysisProfile, "analysisProfile");
        filenameSeparator = required(filenameSeparator, "filenameSeparator");
        catalogueHash = required(catalogueHash, "catalogueHash");
        Objects.requireNonNull(evidence, "evidence must not be null");
        properties = List.copyOf(Objects.requireNonNull(properties, "properties must not be null"));
        if (properties.isEmpty()) {
            throw new IllegalArgumentException("at least one property must be available");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
