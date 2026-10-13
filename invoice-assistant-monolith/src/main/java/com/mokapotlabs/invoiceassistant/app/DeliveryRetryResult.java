package com.mokapotlabs.invoiceassistant.app;

public sealed interface DeliveryRetryResult {
    record Accepted() implements DeliveryRetryResult {}

    record Rejected(String message) implements DeliveryRetryResult {
        public Rejected {
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("retry rejection requires a message");
            }
        }
    }
}
