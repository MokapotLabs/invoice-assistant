package com.mokapotlabs.invoiceassistant.readside;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Typed business outcome of one notification command attempt. */
@Entity
@Table(
        name = "invoice_notification_outcome",
        indexes = {@Index(name = "idx_notification_outcome_review", columnList = "reviewId")})
public class NotificationOutcomeEntity {
    @Id
    public String effectId;

    public String logicalEffectId;

    @Column(nullable = false)
    public UUID documentId;

    public UUID reviewId;

    @Column(nullable = false)
    public Instant observedAt;

    @Column(nullable = false)
    public String status;

    @Lob
    @Column(nullable = false)
    public String message;

    public String externalReference;

    public NotificationOutcomeEntity() {}
}
