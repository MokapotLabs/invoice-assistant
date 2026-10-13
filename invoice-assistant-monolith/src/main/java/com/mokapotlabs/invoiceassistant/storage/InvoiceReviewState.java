package com.mokapotlabs.invoiceassistant.storage;

import com.mokapotlabs.invoiceassistant.domain.ConfirmedInvoice;
import com.mokapotlabs.invoiceassistant.domain.InvoiceReview;
import java.util.Optional;

public record InvoiceReviewState(
        InvoiceAnalysisStatus status,
        Optional<InvoiceReview> review,
        Optional<String> failureMessage,
        Optional<ConfirmedInvoice> confirmation) {
    public InvoiceReviewState {
        status = status == null ? InvoiceAnalysisStatus.PENDING : status;
        review = review == null ? Optional.empty() : review;
        failureMessage = failureMessage == null
                ? Optional.empty()
                : failureMessage.map(String::trim).filter(message -> !message.isEmpty());
        confirmation = confirmation == null ? Optional.empty() : confirmation;
    }

    public static InvoiceReviewState pending() {
        return new InvoiceReviewState(
                InvoiceAnalysisStatus.PENDING, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static InvoiceReviewState ready(InvoiceReview review) {
        return new InvoiceReviewState(
                InvoiceAnalysisStatus.REVIEW_READY, Optional.of(review), Optional.empty(), Optional.empty());
    }

    public static InvoiceReviewState failed(String message) {
        return new InvoiceReviewState(
                InvoiceAnalysisStatus.ANALYSIS_FAILED, Optional.empty(), Optional.of(message), Optional.empty());
    }

    public InvoiceReviewState withConfirmation(ConfirmedInvoice confirmedInvoice) {
        if (status != InvoiceAnalysisStatus.REVIEW_READY || review.isEmpty()) {
            throw new IllegalStateException("An invoice must be review-ready before it can be confirmed.");
        }
        return new InvoiceReviewState(status, review, Optional.empty(), Optional.of(confirmedInvoice));
    }
}
