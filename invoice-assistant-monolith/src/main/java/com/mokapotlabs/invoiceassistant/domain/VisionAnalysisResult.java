package com.mokapotlabs.invoiceassistant.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.pipelineframework.repository.PayloadReference;

/** Trusted vision context plus the narrow model-authored supplier observation. */
public record VisionAnalysisResult(
        UUID reviewId,
        UUID documentId,
        PayloadReference invoice,
        String originalFilename,
        String evidenceHash,
        String catalogueHash,
        String analysisProfile,
        String filenameSeparator,
        List<Property> properties,
        InvoiceReview textReview,
        VisionSupplierObservation observation) {
    public VisionAnalysisResult {
        Objects.requireNonNull(reviewId, "reviewId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(invoice, "invoice must not be null");
        originalFilename = required(originalFilename, "originalFilename");
        evidenceHash = required(evidenceHash, "evidenceHash");
        catalogueHash = required(catalogueHash, "catalogueHash");
        analysisProfile = required(analysisProfile, "analysisProfile");
        filenameSeparator = required(filenameSeparator, "filenameSeparator");
        properties = List.copyOf(Objects.requireNonNull(properties, "properties must not be null"));
        Objects.requireNonNull(textReview, "textReview must not be null");
        Objects.requireNonNull(observation, "observation must not be null");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
