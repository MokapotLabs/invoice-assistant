package com.mokapotlabs.invoiceassistant.domain;

import java.util.Objects;

/** Canonical Await output: suspended authoritative review plus its validated human confirmation. */
public record CompletedConfirmation(ReviewReady review, ConfirmedInvoice confirmedInvoice)
        implements InvoiceApprovalRoute {
    public CompletedConfirmation {
        Objects.requireNonNull(review, "review must not be null");
        Objects.requireNonNull(confirmedInvoice, "confirmedInvoice must not be null");
    }
}
