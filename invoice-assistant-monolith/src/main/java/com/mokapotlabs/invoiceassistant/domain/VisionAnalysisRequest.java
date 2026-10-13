package com.mokapotlabs.invoiceassistant.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.pipelineframework.repository.PayloadReference;

/** Typed vision fallback input; only image is exposed to the model as media. */
public record VisionAnalysisRequest(
        UUID documentId,
        UUID reviewId,
        PayloadReference invoice,
        PayloadReference image,
        String analysisProfile,
        String visionProfile,
        String filenameSeparator,
        String catalogueHash,
        InvoiceEvidence evidence,
        List<Property> properties,
        InvoiceReview textReview) {
    public VisionAnalysisRequest {
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(reviewId);
        Objects.requireNonNull(invoice);
        Objects.requireNonNull(image);
        Objects.requireNonNull(evidence);
        properties = List.copyOf(Objects.requireNonNull(properties));
        Objects.requireNonNull(textReview);
    }
}
