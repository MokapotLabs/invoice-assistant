package com.mokapotlabs.invoiceassistant.web;

import com.mokapotlabs.invoiceassistant.app.UploadResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record InvoiceBatchUploadView(List<Item> items, Optional<ReviewQueueToken> reviewQueue) {
    public InvoiceBatchUploadView {
        items = List.copyOf(items);
        reviewQueue = Objects.requireNonNull(reviewQueue, "reviewQueue must not be null");
    }

    public int acceptedCount() {
        return (int) items.stream().filter(Item::accepted).count();
    }

    public boolean hasAccepted() {
        return reviewQueue.isPresent();
    }

    public record Item(String filename, Status status, String message, Optional<UUID> documentId) {
        public Item {
            filename = Objects.requireNonNull(filename, "filename must not be null");
            status = Objects.requireNonNull(status, "status must not be null");
            message = Objects.requireNonNull(message, "message must not be null");
            documentId = Objects.requireNonNull(documentId, "documentId must not be null");
        }

        public static Item from(String filename, UploadResult result) {
            if (result instanceof UploadResult.Accepted accepted) {
                return new Item(
                        filename,
                        Status.ACCEPTED,
                        "Analysis started.",
                        Optional.of(accepted.document().documentId()));
            }
            if (result instanceof UploadResult.Duplicate duplicate) {
                return new Item(
                        filename,
                        Status.DUPLICATE,
                        "This PDF was already uploaded.",
                        Optional.of(duplicate.existingDocument().documentId()));
            }
            if (result instanceof UploadResult.Rejected rejected) {
                return new Item(filename, Status.REJECTED, rejected.message(), Optional.empty());
            }
            return new Item(filename, Status.FAILED, ((UploadResult.Failed) result).message(), Optional.empty());
        }

        public boolean accepted() {
            return status == Status.ACCEPTED;
        }

        public boolean duplicate() {
            return status == Status.DUPLICATE;
        }

        public String reviewUrl() {
            return documentId.map(id -> "/invoices/" + id + "/review").orElse("");
        }
    }

    public enum Status {
        ACCEPTED,
        DUPLICATE,
        REJECTED,
        FAILED
    }
}
