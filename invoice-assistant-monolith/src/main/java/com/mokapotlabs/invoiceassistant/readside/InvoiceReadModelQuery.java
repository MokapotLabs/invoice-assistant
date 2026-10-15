package com.mokapotlabs.invoiceassistant.readside;

import com.mokapotlabs.invoiceassistant.domain.ConfirmedInvoice;
import com.mokapotlabs.invoiceassistant.domain.DeliveryObligationTruth;
import com.mokapotlabs.invoiceassistant.storage.DeliveryAttempt;
import com.mokapotlabs.invoiceassistant.storage.DeliveryOperationState;
import com.mokapotlabs.invoiceassistant.storage.DeliveryOperationStatus;
import com.mokapotlabs.invoiceassistant.storage.DeliveryState;
import com.mokapotlabs.invoiceassistant.storage.InvoiceAnalysisStatus;
import com.mokapotlabs.invoiceassistant.storage.InvoiceReviewState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Assembles a disposable UI projection from directly queried typed boundary tables. */
@ApplicationScoped
public class InvoiceReadModelQuery {
    private final EntityManager entityManager;
    private final InvoiceAdmissionQuery admissions;
    private final InvoiceReviewQuery reviews;
    private final InvoiceReviewPersistenceMapper reviewMapper;

    public InvoiceReadModelQuery(
            EntityManager entityManager,
            InvoiceAdmissionQuery admissions,
            InvoiceReviewQuery reviews,
            InvoiceReviewPersistenceMapper reviewMapper) {
        this.entityManager = entityManager;
        this.admissions = admissions;
        this.reviews = reviews;
        this.reviewMapper = reviewMapper;
    }

    public List<InvoiceReadModel> all() {
        var latestByDocument = new LinkedHashMap<UUID, InvoiceAdmissionEntity>();
        admissions.all().forEach(admission -> latestByDocument.putIfAbsent(admission.documentId, admission));
        return latestByDocument.values().stream()
                .map(admission -> enrich(new InvoiceReadModel(
                        admissions.document(admission), InvoiceReviewState.pending(), DeliveryState.notStarted())))
                .toList();
    }

    @jakarta.enterprise.context.control.ActivateRequestContext
    public Optional<InvoiceReadModel> find(UUID documentId) {
        return admissions
                .find(documentId)
                .map(admission -> enrich(new InvoiceReadModel(
                        admissions.document(admission), InvoiceReviewState.pending(), DeliveryState.notStarted())));
    }

    public InvoiceReadModel enrich(InvoiceReadModel record) {
        UUID documentId = record.document().documentId();
        return reviews.latest(documentId)
                .map(review -> {
                    InvoiceReadModel reviewed = withReview(record, review);
                    return reviewed.withDeliveryState(delivery(review.reviewId, reviewed.deliveryState()));
                })
                .orElse(record);
    }

    public Optional<InvoiceConfirmationEntity> confirmation(UUID reviewId) {
        return Optional.ofNullable(entityManager.find(InvoiceConfirmationEntity.class, reviewId));
    }

    private InvoiceReadModel withReview(InvoiceReadModel record, InvoiceReviewEntity entity) {
        if (record.reviewState().status() == InvoiceAnalysisStatus.ANALYSIS_FAILED) {
            return record;
        }
        InvoiceReviewState state = InvoiceReviewState.ready(reviewMapper.review(entity));
        Optional<ConfirmedInvoice> confirmation = confirmation(entity.reviewId).map(this::confirmation);
        if (confirmation.isPresent()) {
            state = state.withConfirmation(confirmation.orElseThrow());
        }
        return record.withReviewState(state);
    }

    private ConfirmedInvoice confirmation(InvoiceConfirmationEntity entity) {
        return new ConfirmedInvoice(
                entity.documentId,
                entity.reviewId,
                entity.confirmedPropertyId,
                entity.propertyStreetNumber,
                !entity.recommendedPropertyId.equals(entity.confirmedPropertyId),
                entity.supplierCorrected,
                entity.noteCorrected,
                entity.canonicalFilename,
                entity.confirmedAt,
                entity.confirmedSupplier,
                entity.confirmedNote,
                legacyValue(entity.emailRecipient, "accountant@example.invalid"),
                legacyValue(entity.emailSubject, entity.canonicalFilename),
                legacyValue(entity.emailBody, "Invoice attached."));
    }

    private static String legacyValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private DeliveryState delivery(UUID reviewId, DeliveryState fallback) {
        List<NotificationOutcomeEntity> notification = entityManager
                .createQuery(
                        "from NotificationOutcomeEntity where reviewId = :reviewId order by observedAt",
                        NotificationOutcomeEntity.class)
                .setParameter("reviewId", reviewId)
                .getResultList();
        Optional<InvoiceDeliveryEntity> terminal = entityManager
                .createQuery("from InvoiceDeliveryEntity where reviewId = :reviewId", InvoiceDeliveryEntity.class)
                .setParameter("reviewId", reviewId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();

        DeliveryOperationState notificationState = notification.isEmpty()
                ? terminal.map(entity -> terminalState(
                                entity.notificationTruth,
                                entity.notificationMessage,
                                entity.notificationExternalReference,
                                entity.observedAt))
                        .orElse(fallback.notification())
                : notificationState(notification);
        return new DeliveryState(notificationState);
    }

    private static DeliveryOperationState notificationState(List<NotificationOutcomeEntity> outcomes) {
        List<DeliveryAttempt> attempts = java.util.stream.IntStream.range(0, outcomes.size())
                .mapToObj(index -> attempt(
                        index + 1,
                        outcomes.get(index).status,
                        outcomes.get(index).message,
                        outcomes.get(index).externalReference,
                        outcomes.get(index).observedAt))
                .toList();
        return new DeliveryOperationState(attempts.getLast().status(), attempts);
    }

    private static DeliveryAttempt attempt(
            int number, String status, String message, String externalReference, Instant observedAt) {
        return new DeliveryAttempt(
                number,
                DeliveryOperationStatus.valueOf(status),
                message,
                Optional.ofNullable(externalReference),
                observedAt);
    }

    private static DeliveryOperationState terminalState(
            String truth, String message, String externalReference, Instant observedAt) {
        DeliveryOperationStatus status =
                switch (DeliveryObligationTruth.valueOf(truth)) {
                    case NOT_ATTEMPTED -> DeliveryOperationStatus.NOT_STARTED;
                    case ALREADY_SUCCEEDED, SUCCEEDED -> DeliveryOperationStatus.SUCCEEDED;
                    case FAILED -> DeliveryOperationStatus.FAILED;
                    case USER_ACTION_REQUIRED -> DeliveryOperationStatus.USER_ACTION_REQUIRED;
                    case EXTERNAL_SESSION_REQUIRED -> DeliveryOperationStatus.EXTERNAL_SESSION_REQUIRED;
                    case INTERRUPTED -> DeliveryOperationStatus.INTERRUPTED;
                    case SUBMISSION_UNCERTAIN -> DeliveryOperationStatus.SUBMISSION_UNCERTAIN;
                };
        if (status == DeliveryOperationStatus.NOT_STARTED || message == null || message.isBlank()) {
            return new DeliveryOperationState(status, List.of());
        }
        return new DeliveryOperationState(
                status,
                List.of(new DeliveryAttempt(1, status, message, Optional.ofNullable(externalReference), observedAt)));
    }
}
