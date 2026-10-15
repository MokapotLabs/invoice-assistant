package com.mokapotlabs.invoiceassistant.domain;

import java.util.Objects;

public record NotifyAttempt(NotifyAccountantCommand command) implements InvoiceEntry {
    public NotifyAttempt {
        Objects.requireNonNull(command, "command must not be null");
    }
}
