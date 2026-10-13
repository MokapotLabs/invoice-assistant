package com.mokapotlabs.invoiceassistant.web;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record InvoiceReviewQueueView(ReviewQueueToken token, List<Item> items) {
    public InvoiceReviewQueueView {
        token = Objects.requireNonNull(token, "token must not be null");
        items = List.copyOf(items);
    }

    public Optional<UUID> nextReady() {
        return items.stream()
                .filter(item -> item.status() == Status.READY)
                .map(Item::documentId)
                .findFirst();
    }

    public boolean complete() {
        return items.stream().noneMatch(item -> item.status() == Status.PENDING || item.status() == Status.READY);
    }

    public int handedOffCount() {
        return (int) items.stream()
                .filter(item -> item.status() == Status.HANDED_OFF)
                .count();
    }

    public int failedCount() {
        return (int) items.stream()
                .filter(item -> item.status() == Status.FAILED || item.status() == Status.UNAVAILABLE)
                .count();
    }

    public String nextReviewUrl() {
        return nextReady()
                .map(documentId -> "/invoices/" + documentId + "/review?queue=" + token.encoded())
                .orElse("");
    }

    public record Item(UUID documentId, String filename, Status status, String message) {
        public Item {
            documentId = Objects.requireNonNull(documentId, "documentId must not be null");
            filename = Objects.requireNonNull(filename, "filename must not be null");
            status = Objects.requireNonNull(status, "status must not be null");
            message = Objects.requireNonNull(message, "message must not be null");
        }
    }

    public enum Status {
        PENDING,
        READY,
        FAILED,
        HANDED_OFF,
        UNAVAILABLE
    }
}
