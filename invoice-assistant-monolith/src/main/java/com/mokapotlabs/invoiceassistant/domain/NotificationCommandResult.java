package com.mokapotlabs.invoiceassistant.domain;

import com.mokapotlabs.invoiceassistant.app.NotifyAccountantResult;
import java.util.Objects;

public record NotificationCommandResult(
        NotifyAccountantCommand command, NotifyAccountantResult providerResult, String occurrenceId) {
    public NotificationCommandResult {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(providerResult, "providerResult must not be null");
        if (occurrenceId == null || occurrenceId.isBlank()) {
            throw new IllegalArgumentException("occurrenceId must not be blank");
        }
    }
}
