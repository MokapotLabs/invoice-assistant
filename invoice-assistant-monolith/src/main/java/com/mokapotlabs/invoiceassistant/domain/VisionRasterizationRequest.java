package com.mokapotlabs.invoiceassistant.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.pipelineframework.repository.PayloadReference;

/** Carries unresolved text evidence into deterministic header rasterization. */
public record VisionRasterizationRequest(
        UUID documentId,
        UUID reviewId,
        PayloadReference invoice,
        String analysisProfile,
        String visionProfile,
        String filenameSeparator,
        String catalogueHash,
        InvoiceEvidence evidence,
        List<Property> properties,
        InvoiceReview textReview)
        implements AnalysisRoute {
    public VisionRasterizationRequest {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(reviewId, "reviewId must not be null");
        Objects.requireNonNull(invoice, "invoice must not be null");
        analysisProfile = required(analysisProfile, "analysisProfile");
        visionProfile = required(visionProfile, "visionProfile");
        filenameSeparator = required(filenameSeparator, "filenameSeparator");
        catalogueHash = required(catalogueHash, "catalogueHash");
        Objects.requireNonNull(evidence, "evidence must not be null");
        properties = List.copyOf(Objects.requireNonNull(properties, "properties must not be null"));
        Objects.requireNonNull(textReview, "textReview must not be null");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
