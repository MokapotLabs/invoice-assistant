package com.mokapotlabs.invoiceassistant.web;

import com.mokapotlabs.invoiceassistant.readside.InvoiceReadSide;
import com.mokapotlabs.invoiceassistant.storage.InvoiceAnalysisStatus;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InvoiceReviewQueue {
    private final InvoiceReadSide readSide;

    public InvoiceReviewQueue(InvoiceReadSide readSide) {
        this.readSide = readSide;
    }

    public InvoiceReviewQueueView view(ReviewQueueToken token) {
        var items = token.documentIds().stream()
                .map(documentId -> readSide.find(documentId)
                        .map(record -> {
                            var review = record.reviewState();
                            if (review.confirmation().isPresent()) {
                                return new InvoiceReviewQueueView.Item(
                                        documentId,
                                        record.document().originalFilename(),
                                        InvoiceReviewQueueView.Status.HANDED_OFF,
                                        "Handed off for delivery.");
                            }
                            if (review.status() == InvoiceAnalysisStatus.REVIEW_READY) {
                                return new InvoiceReviewQueueView.Item(
                                        documentId,
                                        record.document().originalFilename(),
                                        InvoiceReviewQueueView.Status.READY,
                                        "Ready for review.");
                            }
                            if (review.status() == InvoiceAnalysisStatus.ANALYSIS_FAILED) {
                                return new InvoiceReviewQueueView.Item(
                                        documentId,
                                        record.document().originalFilename(),
                                        InvoiceReviewQueueView.Status.FAILED,
                                        review.failureMessage().orElse("Analysis failed."));
                            }
                            return new InvoiceReviewQueueView.Item(
                                    documentId,
                                    record.document().originalFilename(),
                                    InvoiceReviewQueueView.Status.PENDING,
                                    "Analysis is pending.");
                        })
                        .orElseGet(() -> new InvoiceReviewQueueView.Item(
                                documentId,
                                "Unavailable invoice",
                                InvoiceReviewQueueView.Status.UNAVAILABLE,
                                "This invoice is no longer available to review.")))
                .toList();
        return new InvoiceReviewQueueView(token, items);
    }
}
