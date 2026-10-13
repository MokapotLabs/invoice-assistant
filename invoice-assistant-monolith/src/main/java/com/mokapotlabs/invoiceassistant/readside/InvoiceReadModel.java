package com.mokapotlabs.invoiceassistant.readside;

import com.mokapotlabs.invoiceassistant.domain.InvoiceDocument;
import com.mokapotlabs.invoiceassistant.storage.DeliveryState;
import com.mokapotlabs.invoiceassistant.storage.InvoiceReviewState;
import com.mokapotlabs.invoiceassistant.storage.InvoiceWorkflowFailure;
import java.util.Objects;
import java.util.Optional;

/** Disposable UI projection assembled from typed persistence and live execution state. */
public record InvoiceReadModel(
        InvoiceDocument document,
        InvoiceReviewState reviewState,
        DeliveryState deliveryState,
        Optional<InvoiceWorkflowFailure> workflowFailure) {
    public InvoiceReadModel {
        Objects.requireNonNull(document, "document must not be null");
        reviewState = reviewState == null ? InvoiceReviewState.pending() : reviewState;
        deliveryState = deliveryState == null ? DeliveryState.notStarted() : deliveryState;
        workflowFailure = workflowFailure == null ? Optional.empty() : workflowFailure;
    }

    public InvoiceReadModel(InvoiceDocument document, InvoiceReviewState reviewState) {
        this(document, reviewState, DeliveryState.notStarted(), Optional.empty());
    }

    public InvoiceReadModel(InvoiceDocument document, InvoiceReviewState reviewState, DeliveryState deliveryState) {
        this(document, reviewState, deliveryState, Optional.empty());
    }

    public InvoiceReadModel withDeliveryState(DeliveryState updatedDeliveryState) {
        return new InvoiceReadModel(document, reviewState, updatedDeliveryState, workflowFailure);
    }

    public InvoiceReadModel withReviewState(InvoiceReviewState updatedReviewState) {
        return new InvoiceReadModel(document, updatedReviewState, deliveryState, workflowFailure);
    }

    public InvoiceReadModel withWorkflowFailure(InvoiceWorkflowFailure failure) {
        return new InvoiceReadModel(document, reviewState, deliveryState, Optional.of(failure));
    }

    public InvoiceReadModel withoutWorkflowFailure() {
        return new InvoiceReadModel(document, reviewState, deliveryState, Optional.empty());
    }
}
