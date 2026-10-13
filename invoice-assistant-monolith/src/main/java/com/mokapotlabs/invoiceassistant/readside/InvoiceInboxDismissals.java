package com.mokapotlabs.invoiceassistant.readside;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** UI-only dismissal projection; it is never consulted by pipeline execution. */
@ApplicationScoped
public class InvoiceInboxDismissals {
    private final EntityManager entityManager;

    public InvoiceInboxDismissals(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Set<UUID> documentIds() {
        return entityManager
                .createQuery("select dismissal.documentId from InvoiceInboxDismissalEntity dismissal", UUID.class)
                .getResultStream()
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional
    public void dismiss(UUID documentId) {
        if (Optional.ofNullable(entityManager.find(InvoiceInboxDismissalEntity.class, documentId))
                .isPresent()) {
            return;
        }
        var dismissal = new InvoiceInboxDismissalEntity();
        dismissal.documentId = documentId;
        dismissal.dismissedAt = Instant.now();
        entityManager.persist(dismissal);
    }
}
