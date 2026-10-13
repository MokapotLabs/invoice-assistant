package com.mokapotlabs.invoiceassistant.readside;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Typed terminal truth for the email submission. */
@Entity
@Table(name = "invoice_delivery")
public class InvoiceDeliveryEntity {
    @Id
    public UUID documentId;

    public UUID reviewId;

    public Instant observedAt;
    public String notificationTruth;

    @Lob
    public String notificationMessage;

    public String notificationExternalReference;

    public InvoiceDeliveryEntity() {}
}
