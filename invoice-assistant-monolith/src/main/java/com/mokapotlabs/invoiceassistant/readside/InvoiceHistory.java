package com.mokapotlabs.invoiceassistant.readside;

import java.util.List;
import java.util.Optional;

/** Typed audit view assembled directly from the persisted business tables. */
public record InvoiceHistory(
        Optional<InvoiceReviewEntity> review,
        Optional<InvoiceConfirmationEntity> confirmation,
        List<NotificationOutcomeEntity> notificationOutcomes,
        Optional<InvoiceDeliveryEntity> delivery) {
    public InvoiceHistory {
        review = review == null ? Optional.empty() : review;
        confirmation = confirmation == null ? Optional.empty() : confirmation;
        notificationOutcomes = notificationOutcomes == null ? List.of() : List.copyOf(notificationOutcomes);
        delivery = delivery == null ? Optional.empty() : delivery;
    }
}
