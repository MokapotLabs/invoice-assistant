package com.mokapotlabs.invoiceassistant.domain;

import com.mokapotlabs.invoiceassistant.interaction.PropertyConfirmationRequest;

public sealed interface InvoiceApprovalRoute {
    record Manual(PropertyConfirmationRequest value) implements InvoiceApprovalRoute {}

    record Automatic(CompletedConfirmation value) implements InvoiceApprovalRoute {}
}
