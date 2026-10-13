package com.mokapotlabs.invoiceassistant.domain;

import java.util.Objects;

public record NotifyAccountantCommand(InvoiceDocument document, ConfirmedInvoice confirmedInvoice) {
    public NotifyAccountantCommand {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(confirmedInvoice, "confirmedInvoice must not be null");
    }
}
