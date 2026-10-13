package com.mokapotlabs.invoiceassistant.storage;

import java.util.Objects;

public record DeliveryState(DeliveryOperationState notification) {
    public DeliveryState {
        Objects.requireNonNull(notification);
    }

    public static DeliveryState notStarted() {
        return new DeliveryState(DeliveryOperationState.notStarted());
    }

    public boolean canStart() {
        return notification.status() == DeliveryOperationStatus.NOT_STARTED;
    }

    public boolean inProgress() {
        return notification.status() == DeliveryOperationStatus.RUNNING;
    }

    public DeliveryState withNotification(DeliveryOperationState updated) {
        return new DeliveryState(updated);
    }
}
