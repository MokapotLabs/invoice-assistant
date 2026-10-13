package com.mokapotlabs.invoiceassistant.readside;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Query-shaped persistence representation of the ReviewReady business boundary. */
@Entity
@Table(
        name = "invoice_review",
        indexes = @Index(name = "idx_invoice_review_document", columnList = "documentId,observedAt"))
public class InvoiceReviewEntity {
    @Id
    public UUID reviewId;

    @Column(nullable = false)
    public UUID documentId;

    @Column(nullable = false)
    public Instant observedAt;

    @Column(nullable = false)
    public String originalFilename;

    @Column(nullable = false)
    public String evidenceHash;

    @Column(nullable = false)
    public String catalogueHash;

    @Column(nullable = false)
    public String analysisProfile;

    @Column(nullable = false)
    public String supplier;

    @Column(nullable = false)
    public String supplierEvidenceStatus;

    @Column(nullable = false)
    public String invoiceNumber;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal totalAmount;

    @Lob
    @Column(nullable = false)
    public String diagnostic;

    @Column(nullable = false)
    public String recommendedPropertyId;

    @Lob
    @Column(nullable = false)
    public String recommendationExplanation;

    @Lob
    @Column(nullable = false)
    public String note;

    public InvoiceReviewEntity() {}
}
