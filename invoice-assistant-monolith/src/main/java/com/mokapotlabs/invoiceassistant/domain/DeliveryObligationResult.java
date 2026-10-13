package com.mokapotlabs.invoiceassistant.domain;

import java.util.Optional;

public record DeliveryObligationResult(
        DeliveryObligationTruth truth, Optional<String> message, Optional<String> externalReference) {
    public DeliveryObligationResult {
        if (truth == null) {
            throw new IllegalArgumentException("truth must not be null");
        }
        message = message == null ? Optional.empty() : message.filter(value -> !value.isBlank());
        externalReference =
                externalReference == null ? Optional.empty() : externalReference.filter(value -> !value.isBlank());
    }

    public static DeliveryObligationResult notAttempted() {
        return new DeliveryObligationResult(DeliveryObligationTruth.NOT_ATTEMPTED, Optional.empty(), Optional.empty());
    }
}
