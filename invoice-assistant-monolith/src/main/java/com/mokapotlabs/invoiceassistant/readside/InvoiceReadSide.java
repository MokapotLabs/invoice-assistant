package com.mokapotlabs.invoiceassistant.readside;

import com.mokapotlabs.invoiceassistant.analysis.InvoiceExecutionTracker;
import com.mokapotlabs.invoiceassistant.storage.DeliveryOperationStatus;
import com.mokapotlabs.invoiceassistant.storage.InvoiceInbox;
import com.mokapotlabs.invoiceassistant.storage.InvoiceInboxItem;
import com.mokapotlabs.invoiceassistant.storage.InvoiceReviewState;
import com.mokapotlabs.invoiceassistant.storage.InvoiceWorkflowFailure;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/** UI read side over typed persisted business facts plus live TPF execution observations. */
@ApplicationScoped
public class InvoiceReadSide {
    private static final String INTERRUPTED_ANALYSIS_MESSAGE =
            "Invoice analysis did not complete before the application restarted.";
    private final InvoiceReadModelQuery persisted;
    private final InvoiceExecutionTracker executions;

    public InvoiceReadSide(InvoiceReadModelQuery persisted, InvoiceExecutionTracker executions) {
        this.persisted = persisted;
        this.executions = executions;
    }

    public InvoiceInbox inbox() {
        var documents = persisted.all().stream()
                .map(this::withExecutionState)
                .map(InvoiceReadSide::item)
                .sorted(Comparator.comparing(item -> item.document().uploadedAt(), Comparator.reverseOrder()))
                .toList();
        return new InvoiceInbox(documents, java.util.List.of());
    }

    public Optional<InvoiceReadModel> find(UUID documentId) {
        return persisted.find(documentId).map(this::withExecutionState);
    }

    private InvoiceReadModel withExecutionState(InvoiceReadModel record) {
        var observed = executions.state(record.document().documentId());
        if (observed.isPresent()) {
            var state = observed.orElseThrow();
            if (state.failure().isPresent()) {
                return failed(record, state.phase(), state.failure().orElseThrow());
            }
            if (state.phase() == InvoiceExecutionTracker.Phase.DELIVERY) {
                var delivery = record.deliveryState();
                delivery = delivery.withNotification(delivery.notification().running());
                return record.withDeliveryState(delivery).withoutWorkflowFailure();
            }
            return record.withoutWorkflowFailure();
        }
        if (record.reviewState().review().isEmpty()) {
            var failure = new InvoiceWorkflowFailure(
                    "Invoice analysis",
                    "application-restarted",
                    INTERRUPTED_ANALYSIS_MESSAGE,
                    record.document().uploadedAt());
            return record.withReviewState(InvoiceReviewState.failed(failure.displayMessage()))
                    .withWorkflowFailure(failure);
        }
        if (record.reviewState().confirmation().isPresent()
                && record.deliveryState().notification().status() != DeliveryOperationStatus.SUCCEEDED
                && record.deliveryState().notification().status() != DeliveryOperationStatus.SUBMISSION_UNCERTAIN) {
            return failed(
                    record,
                    InvoiceExecutionTracker.Phase.DELIVERY,
                    new InvoiceWorkflowFailure(
                            "Invoice delivery",
                            "delivery-interrupted",
                            "The application stopped before delivery was recorded. Check Outlook Sent Items before retrying.",
                            record.reviewState().confirmation().orElseThrow().confirmedAt()));
        }
        return record;
    }

    private static InvoiceReadModel failed(
            InvoiceReadModel record, InvoiceExecutionTracker.Phase phase, InvoiceWorkflowFailure failure) {
        if (phase == InvoiceExecutionTracker.Phase.ANALYSIS) {
            return record.withReviewState(InvoiceReviewState.failed(failure.displayMessage()))
                    .withWorkflowFailure(failure);
        }
        var delivery = record.deliveryState();
        DeliveryOperationStatus status = Arrays.stream(DeliveryOperationStatus.values())
                .filter(candidate -> switch (candidate) {
                    case FAILED, EXTERNAL_SESSION_REQUIRED, USER_ACTION_REQUIRED, INTERRUPTED -> true;
                    default -> false;
                })
                .filter(candidate -> failure.message().contains("delivery-status=" + candidate.name() + ";"))
                .findFirst()
                .orElse(DeliveryOperationStatus.FAILED);
        delivery = delivery.withNotification(
                delivery.notification().record(status, failure.displayMessage(), Optional.empty()));
        return record.withDeliveryState(delivery).withWorkflowFailure(failure);
    }

    private static InvoiceInboxItem item(InvoiceReadModel record) {
        return new InvoiceInboxItem(record.document(), record.reviewState(), record.deliveryState(), true, "Available");
    }
}
