package com.mokapotlabs.invoiceassistant.domain;

import java.util.Objects;

public record AutomaticDelivery(String propertyId, String recipient, boolean ready) {
    public AutomaticDelivery {
        Objects.requireNonNull(propertyId);
        Objects.requireNonNull(recipient);
        if (propertyId.isBlank()) throw new IllegalArgumentException("propertyId must not be blank");
    }
}
