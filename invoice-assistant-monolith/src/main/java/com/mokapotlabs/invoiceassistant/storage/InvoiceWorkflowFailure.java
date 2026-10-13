package com.mokapotlabs.invoiceassistant.storage;

import java.time.Instant;

/** Durable, user-visible detail for an unexpected workflow execution failure. */
public record InvoiceWorkflowFailure(String operation, String code, String message, Instant occurredAt) {
    private static final int MAX_DETAIL_LENGTH = 1_000;

    public InvoiceWorkflowFailure {
        operation = required(operation, "operation");
        code = required(code, "code");
        message = required(message, "message");
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public String displayMessage() {
        String detail = message.contains(code) ? message : code + ": " + message;
        return operation + " failed — " + detail;
    }

    public static String safeDetail(String value, String fallback) {
        String detail = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (detail.isEmpty()) {
            detail = fallback;
        }
        return detail.length() <= MAX_DETAIL_LENGTH ? detail : detail.substring(0, MAX_DETAIL_LENGTH) + "…";
    }

    public static String safeDetail(Throwable failure, String fallback) {
        if (failure == null) {
            return safeDetail((String) null, fallback);
        }
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return safeDetail(current.getMessage(), fallback);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
