package com.mokapotlabs.invoiceassistant.interaction;

import com.mokapotlabs.invoiceassistant.domain.ReviewReady;
import java.util.Objects;
import java.util.UUID;

/** The ordinary initiating operation captures the authoritative review for deferred completion. */
public record PropertyConfirmationRequest(UUID documentId, ReviewReady review) {
    public PropertyConfirmationRequest(ReviewReady review) {
        this(review.documentId(), review);
    }

    public PropertyConfirmationRequest {
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(review);
        if (!documentId.equals(review.documentId())) {
            throw new IllegalArgumentException("confirmation request must belong to its captured review");
        }
    }
}
