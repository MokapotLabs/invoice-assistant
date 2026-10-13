package com.mokapotlabs.invoiceassistant.storage;

import java.time.Instant;
import java.util.Optional;

public record DeliveryAttempt(
        int attemptNumber,
        DeliveryOperationStatus status,
        String message,
        Optional<String> externalReference,
        Instant completedAt) {
    public DeliveryAttempt {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be positive");
        }
        if (status == null || status == DeliveryOperationStatus.NOT_STARTED) {
            throw new IllegalArgumentException("status must describe a completed attempt");
        }
        message = message == null || message.isBlank() ? "No detail was supplied." : message.trim();
        externalReference =
                externalReference == null ? Optional.empty() : externalReference.filter(value -> !value.isBlank());
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt must not be null");
        }
    }
}
