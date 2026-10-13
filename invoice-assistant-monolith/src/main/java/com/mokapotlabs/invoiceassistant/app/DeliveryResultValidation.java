package com.mokapotlabs.invoiceassistant.app;

import com.mokapotlabs.invoiceassistant.storage.DeliveryOperationStatus;
import java.util.Optional;

final class DeliveryResultValidation {
    private DeliveryResultValidation() {}

    static void validate(DeliveryOperationStatus status, String message, Optional<String> externalReference) {
        if (status == null || status == DeliveryOperationStatus.NOT_STARTED) {
            throw new IllegalArgumentException("A provider result must describe a completed delivery operation");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("A provider result must include a message");
        }
        if (externalReference == null) {
            throw new IllegalArgumentException("externalReference must not be null");
        }
    }
}
