package com.mokapotlabs.invoiceassistant.domain;

import java.util.Objects;
import java.util.UUID;

/** Final projection for one execution; each obligation remains independently truthful. */
public record InvoiceDeliveryResult(UUID documentId, UUID reviewId, DeliveryObligationResult notification) {
    public InvoiceDeliveryResult {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(reviewId, "reviewId must not be null");
        Objects.requireNonNull(notification, "notification must not be null");
    }
}
