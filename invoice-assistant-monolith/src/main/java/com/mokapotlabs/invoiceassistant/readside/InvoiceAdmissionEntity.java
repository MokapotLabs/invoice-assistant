package com.mokapotlabs.invoiceassistant.readside;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Query-shaped persistence representation of the InvoiceFiles admission boundary. */
@Entity
@Table(
        name = "invoice_admission",
        indexes = {
            @Index(name = "idx_invoice_admission_document", columnList = "documentId,observedAt"),
            @Index(name = "idx_invoice_admission_sha256", columnList = "sha256,observedAt")
        })
public class InvoiceAdmissionEntity {
    @Id
    public UUID admissionId;

    @Column(nullable = false)
    public UUID documentId;

    @Column(nullable = false)
    public Instant observedAt;

    @Column(nullable = false)
    public String originalFilename;

    @Column(nullable = false)
    public String mediaType;

    @Column(nullable = false)
    public long sizeBytes;

    @Column(nullable = false, length = 128)
    public String sha256;

    @Lob
    @Column(nullable = false)
    public byte[] invoiceReference;

    @Lob
    @Column(nullable = false)
    public byte[] catalogueReference;

    @Column(nullable = false, length = 128)
    public String invoiceSourceIdentity;

    @Column(nullable = false, length = 128)
    public String catalogueSourceIdentity;

    public InvoiceAdmissionEntity() {}
}
