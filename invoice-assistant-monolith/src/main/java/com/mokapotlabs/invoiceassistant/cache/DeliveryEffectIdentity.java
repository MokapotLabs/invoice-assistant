package com.mokapotlabs.invoiceassistant.cache;

import com.mokapotlabs.invoiceassistant.domain.NotifyAccountantCommand;
import java.util.UUID;

/** Stable logical identities for delivery effects. TPF owns per-dispatch attempt identity. */
public final class DeliveryEffectIdentity {
    private DeliveryEffectIdentity() {}

    public static String notification(NotifyAccountantCommand command) {
        return notification(command.confirmedInvoice().reviewId());
    }

    public static String notification(UUID reviewId) {
        if (reviewId == null) {
            throw new IllegalArgumentException("reviewId must not be null");
        }
        return "notification:v1:" + reviewId;
    }
}
