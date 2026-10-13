package com.mokapotlabs.invoiceassistant.domain;

import java.util.Objects;

public record NotifyAttempt(NotifyAccountantCommand command) {
    public NotifyAttempt {
        Objects.requireNonNull(command, "command must not be null");
    }
}
