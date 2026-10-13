package com.mokapotlabs.invoiceassistant.storage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record DeliveryOperationState(DeliveryOperationStatus status, List<DeliveryAttempt> attempts) {
    public DeliveryOperationState {
        status = status == null ? DeliveryOperationStatus.NOT_STARTED : status;
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
        if (status == DeliveryOperationStatus.NOT_STARTED && !attempts.isEmpty()) {
            throw new IllegalArgumentException("A not-started operation cannot have attempts");
        }
    }

    public static DeliveryOperationState notStarted() {
        return new DeliveryOperationState(DeliveryOperationStatus.NOT_STARTED, List.of());
    }

    public DeliveryOperationState running() {
        return new DeliveryOperationState(DeliveryOperationStatus.RUNNING, attempts);
    }

    public DeliveryOperationState record(
            DeliveryOperationStatus resultStatus, String message, Optional<String> externalReference) {
        DeliveryAttempt attempt =
                new DeliveryAttempt(attempts.size() + 1, resultStatus, message, externalReference, Instant.now());
        return new DeliveryOperationState(resultStatus, append(attempt));
    }

    public boolean retryable() {
        return switch (status) {
            case FAILED, USER_ACTION_REQUIRED, EXTERNAL_SESSION_REQUIRED, INTERRUPTED -> true;
            case NOT_STARTED, RUNNING, SUCCEEDED, SUBMISSION_UNCERTAIN -> false;
        };
    }

    public String displayStatus() {
        return switch (status) {
            case NOT_STARTED -> "Not started";
            case RUNNING -> "In progress";
            case SUCCEEDED -> !attempts.isEmpty()
                            && attempts.getLast().message().startsWith("Accepted by Outlook")
                    ? "Accepted by Outlook"
                    : "Success";
            case FAILED -> "Failure";
            case USER_ACTION_REQUIRED -> "User action required";
            case EXTERNAL_SESSION_REQUIRED -> "External session required";
            case INTERRUPTED -> "Interrupted";
            case SUBMISSION_UNCERTAIN -> "Send uncertain: check Outlook";
        };
    }

    private List<DeliveryAttempt> append(DeliveryAttempt attempt) {
        java.util.ArrayList<DeliveryAttempt> updated = new java.util.ArrayList<>(attempts);
        updated.add(attempt);
        return List.copyOf(updated);
    }
}
