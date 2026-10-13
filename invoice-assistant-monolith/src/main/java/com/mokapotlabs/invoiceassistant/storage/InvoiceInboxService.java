package com.mokapotlabs.invoiceassistant.storage;

import com.mokapotlabs.invoiceassistant.readside.InvoiceInboxDismissals;
import com.mokapotlabs.invoiceassistant.readside.InvoiceReadSide;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InvoiceInboxService {
    private final InvoiceReadSide readSide;
    private final InvoiceInboxDismissals dismissals;

    public InvoiceInboxService(InvoiceReadSide readSide, InvoiceInboxDismissals dismissals) {
        this.readSide = readSide;
        this.dismissals = dismissals;
    }

    public InvoiceInbox inbox() {
        InvoiceInbox inbox = readSide.inbox();
        var dismissed = dismissals.documentIds();
        return new InvoiceInbox(
                inbox.documents().stream()
                        .filter(item -> !dismissed.contains(item.document().documentId()))
                        .toList(),
                inbox.issues());
    }

    public void dismiss(java.util.UUID documentId) {
        dismissals.dismiss(documentId);
    }
}
