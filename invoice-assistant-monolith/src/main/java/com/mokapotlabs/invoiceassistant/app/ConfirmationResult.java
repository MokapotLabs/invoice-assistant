package com.mokapotlabs.invoiceassistant.app;

public sealed interface ConfirmationResult permits ConfirmationResult.Accepted, ConfirmationResult.Rejected {
    record Accepted() implements ConfirmationResult {}

    record Rejected(String message) implements ConfirmationResult {}
}
