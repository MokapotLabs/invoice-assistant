package com.mokapotlabs.invoiceassistant.app;

import com.mokapotlabs.invoiceassistant.storage.DeliveryOperationStatus;
import java.util.Optional;

public record NotifyAccountantResult(
        DeliveryOperationStatus status, String message, Optional<String> externalReference) {
    public NotifyAccountantResult {
        DeliveryResultValidation.validate(status, message, externalReference);
        message = message.trim();
        externalReference =
                externalReference == null ? Optional.empty() : externalReference.filter(value -> !value.isBlank());
    }
}
