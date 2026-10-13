package com.mokapotlabs.invoiceassistant.readside;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;

/** Direct JPA query over typed persisted invoice boundary representations. */
@ApplicationScoped
public class InvoiceHistoryQuery {
    private final EntityManager entityManager;
    private final InvoiceReviewQuery reviews;

    public InvoiceHistoryQuery(EntityManager entityManager, InvoiceReviewQuery reviews) {
        this.entityManager = entityManager;
        this.reviews = reviews;
    }

    public InvoiceHistory history(UUID documentId) {
        Optional<InvoiceReviewEntity> review = reviews.latest(documentId);
        if (review.isEmpty()) {
            return new InvoiceHistory(Optional.empty(), Optional.empty(), java.util.List.of(), Optional.empty());
        }
        UUID reviewId = review.orElseThrow().reviewId;
        return new InvoiceHistory(
                review,
                Optional.ofNullable(entityManager.find(InvoiceConfirmationEntity.class, reviewId)),
                entityManager
                        .createQuery(
                                "from NotificationOutcomeEntity where reviewId = :reviewId order by observedAt",
                                NotificationOutcomeEntity.class)
                        .setParameter("reviewId", reviewId)
                        .getResultList(),
                entityManager
                        .createQuery(
                                "from InvoiceDeliveryEntity where reviewId = :reviewId", InvoiceDeliveryEntity.class)
                        .setParameter("reviewId", reviewId)
                        .setMaxResults(1)
                        .getResultStream()
                        .findFirst());
    }
}
