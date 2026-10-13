package com.mokapotlabs.invoiceassistant.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.pipelineframework.repository.PayloadReference;

/**
 * Metadata for a locally controlled invoice document. The PDF itself is kept out of application state.
 */
public record InvoiceDocument(
        UUID documentId,
        String originalFilename,
        String mediaType,
        long sizeBytes,
        String sha256,
        Instant uploadedAt,
        PayloadReference payloadReference) {
    public InvoiceDocument {
        Objects.requireNonNull(documentId, "documentId must not be null");
        originalFilename = required(originalFilename, "originalFilename");
        mediaType = required(mediaType, "mediaType");
        sha256 = required(sha256, "sha256");
        Objects.requireNonNull(uploadedAt, "uploadedAt must not be null");
        Objects.requireNonNull(payloadReference, "payloadReference must not be null");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
