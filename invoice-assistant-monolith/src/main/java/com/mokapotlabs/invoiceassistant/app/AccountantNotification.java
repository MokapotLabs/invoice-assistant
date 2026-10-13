package com.mokapotlabs.invoiceassistant.app;

import com.mokapotlabs.invoiceassistant.domain.ConfirmedInvoice;
import com.mokapotlabs.invoiceassistant.domain.InvoiceDocument;

public interface AccountantNotification {
    NotifyAccountantResult notifyAccountant(InvoiceDocument document, ConfirmedInvoice confirmedInvoice);
}
