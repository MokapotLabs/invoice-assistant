package com.mokapotlabs.invoiceassistant.web;

import java.util.Objects;

public record ReviewQueueNavigation(boolean active, String token, int position, int total) {
    public ReviewQueueNavigation {
        token = Objects.requireNonNull(token, "token must not be null");
        if (!active && (!token.isEmpty() || position != 0 || total != 0)) {
            throw new IllegalArgumentException("inactive review navigation must be empty");
        }
        if (active && (token.isBlank() || position < 1 || total < position)) {
            throw new IllegalArgumentException("active review navigation is invalid");
        }
    }

    public static ReviewQueueNavigation inactive() {
        return new ReviewQueueNavigation(false, "", 0, 0);
    }

    public static ReviewQueueNavigation active(ReviewQueueToken queue, java.util.UUID documentId) {
        int index = queue.documentIds().indexOf(documentId);
        if (index < 0) {
            throw new IllegalArgumentException("current invoice is not part of the review queue");
        }
        return new ReviewQueueNavigation(
                true, queue.encoded(), index + 1, queue.documentIds().size());
    }
}
