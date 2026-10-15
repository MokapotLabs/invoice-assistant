package com.mokapotlabs.invoiceassistant.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.pipelineframework.repository.PayloadReference;

public record ReviewReady(
        UUID reviewId,
        UUID documentId,
        PayloadReference invoice,
        String originalFilename,
        String evidenceHash,
        String catalogueHash,
        String analysisProfile,
        String filenameSeparator,
        List<Property> properties,
        InvoiceReview review,
        Optional<InvoiceEvidence> evidence)
        implements AnalysisRoute, InvoiceApprovalRoute, InvoiceEntry {
    public ReviewReady(
            UUID reviewId,
            UUID documentId,
            PayloadReference invoice,
            String originalFilename,
            String evidenceHash,
            String catalogueHash,
            String analysisProfile,
            String filenameSeparator,
            List<Property> properties,
            InvoiceReview review) {
        this(
                reviewId,
                documentId,
                invoice,
                originalFilename,
                evidenceHash,
                catalogueHash,
                analysisProfile,
                filenameSeparator,
                properties,
                review,
                Optional.empty());
    }

    public ReviewReady {
        Objects.requireNonNull(evidence);
        Objects.requireNonNull(reviewId, "reviewId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(invoice, "invoice must not be null");
        originalFilename = required(originalFilename, "originalFilename");
        evidenceHash = required(evidenceHash, "evidenceHash");
        catalogueHash = required(catalogueHash, "catalogueHash");
        analysisProfile = required(analysisProfile, "analysisProfile");
        filenameSeparator = required(filenameSeparator, "filenameSeparator");
        properties = List.copyOf(Objects.requireNonNull(properties, "properties must not be null"));
        if (properties.isEmpty()) throw new IllegalArgumentException("properties must not be empty");
        Objects.requireNonNull(review, "review must not be null");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
