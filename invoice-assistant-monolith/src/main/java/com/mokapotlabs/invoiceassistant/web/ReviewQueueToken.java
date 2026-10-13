package com.mokapotlabs.invoiceassistant.web;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record ReviewQueueToken(List<UUID> documentIds) {
    public ReviewQueueToken {
        documentIds = List.copyOf(documentIds);
        if (documentIds.isEmpty()) {
            throw new IllegalArgumentException("review queue must contain at least one invoice");
        }
        if (new LinkedHashSet<>(documentIds).size() != documentIds.size()) {
            throw new IllegalArgumentException("review queue must not contain duplicate invoices");
        }
    }

    public static ReviewQueueToken parse(String encoded, int maxFiles) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("review queue is missing");
        }
        String[] values = encoded.split(",", -1);
        if (values.length > maxFiles) {
            throw new IllegalArgumentException("review queue exceeds the upload batch limit");
        }
        try {
            return new ReviewQueueToken(java.util.Arrays.stream(values)
                    .map(String::trim)
                    .map(UUID::fromString)
                    .toList());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("review queue contains an invalid invoice identifier", exception);
        }
    }

    public static ReviewQueueToken of(List<UUID> documentIds, int maxFiles) {
        if (documentIds.size() > maxFiles) {
            throw new IllegalArgumentException("review queue exceeds the upload batch limit");
        }
        return new ReviewQueueToken(documentIds);
    }

    public String encoded() {
        return documentIds.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    public boolean contains(UUID documentId) {
        return documentIds.contains(documentId);
    }
}
