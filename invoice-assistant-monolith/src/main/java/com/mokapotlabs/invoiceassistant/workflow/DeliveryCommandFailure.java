package com.mokapotlabs.invoiceassistant.workflow;

import com.mokapotlabs.invoiceassistant.storage.DeliveryOperationStatus;
import java.util.Objects;

/** A known provider failure that is safe for TPF to retain as FAILED_RETRYABLE. */
public final class DeliveryCommandFailure extends RuntimeException {
    private final DeliveryOperationStatus status;

    public DeliveryCommandFailure(DeliveryOperationStatus status, String message) {
        super(message == null || message.isBlank() ? "Delivery provider failed without detail." : message.trim());
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (status == DeliveryOperationStatus.SUCCEEDED) {
            throw new IllegalArgumentException("a successful delivery is not a command failure");
        }
    }

    public DeliveryOperationStatus status() {
        return status;
    }
}
