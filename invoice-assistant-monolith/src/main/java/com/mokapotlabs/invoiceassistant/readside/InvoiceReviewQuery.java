package com.mokapotlabs.invoiceassistant.readside;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;

/** Direct typed read-side query for persisted invoice reviews. */
@ApplicationScoped
public class InvoiceReviewQuery {
    private final EntityManager entityManager;

    public InvoiceReviewQuery(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<InvoiceReviewEntity> latest(UUID documentId) {
        return entityManager
                .createQuery(
                        "from InvoiceReviewEntity where documentId = :documentId "
                                + "order by observedAt desc, reviewId desc",
                        InvoiceReviewEntity.class)
                .setParameter("documentId", documentId)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }
}
