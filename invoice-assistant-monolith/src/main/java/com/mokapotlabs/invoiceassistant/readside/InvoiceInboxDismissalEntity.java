package com.mokapotlabs.invoiceassistant.readside;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Disposable UI preference that hides one invoice from the inbox without deleting business history. */
@Entity
@Table(name = "invoice_inbox_dismissal")
public class InvoiceInboxDismissalEntity {
    @Id
    public UUID documentId;

    @Column(nullable = false)
    public Instant dismissedAt;

    public InvoiceInboxDismissalEntity() {}
}
