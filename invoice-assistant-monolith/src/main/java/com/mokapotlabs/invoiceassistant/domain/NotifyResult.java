package com.mokapotlabs.invoiceassistant.domain;

import java.util.Objects;

public record NotifyResult(NotificationCommandResult result) {
    public NotifyResult {
        Objects.requireNonNull(result, "result must not be null");
    }
}
