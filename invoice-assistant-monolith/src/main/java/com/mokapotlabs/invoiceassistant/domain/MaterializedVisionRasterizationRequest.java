package com.mokapotlabs.invoiceassistant.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** File representation of {@link VisionRasterizationRequest}; constructed by TPF per invocation. */
public record MaterializedVisionRasterizationRequest(
        UUID documentId,
        UUID reviewId,
        Path invoice,
        String analysisProfile,
        String visionProfile,
        String filenameSeparator,
        String catalogueHash,
        InvoiceEvidence evidence,
        List<Property> properties,
        InvoiceReview textReview) {
    public MaterializedVisionRasterizationRequest {
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(reviewId);
        Objects.requireNonNull(invoice);
        Objects.requireNonNull(evidence);
        properties = List.copyOf(Objects.requireNonNull(properties));
        Objects.requireNonNull(textReview);
    }
}
