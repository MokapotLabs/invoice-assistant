package com.mokapotlabs.invoiceassistant.readside;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Query-shaped audit representation of one completed human confirmation. */
@Entity
@Table(
        name = "invoice_confirmation",
        indexes = @Index(name = "idx_invoice_confirmation_document", columnList = "documentId,confirmedAt"))
public class InvoiceConfirmationEntity {
    @Id
    public UUID reviewId;

    @Column(nullable = false)
    public UUID documentId;

    @Column(nullable = false)
    public Instant confirmedAt;

    @Column(nullable = false)
    public String recommendedPropertyId;

    @Column(nullable = false)
    public String confirmedPropertyId;

    @Column(nullable = false)
    public String propertyStreetNumber;

    @Column(nullable = false)
    public String canonicalFilename;

    @Column(nullable = false)
    public String recommendedSupplier;

    @Column(nullable = false)
    public String confirmedSupplier;

    @Column(nullable = false)
    public boolean supplierCorrected;

    @Lob
    @Column(nullable = false)
    public String recommendedNote;

    @Lob
    @Column(nullable = false)
    public String confirmedNote;

    @Column(nullable = false)
    public boolean noteCorrected;

    @Column
    public String emailRecipient;

    @Lob
    @Column
    public String emailSubject;

    @Lob
    @Column
    public String emailBody;

    public InvoiceConfirmationEntity() {}
}
