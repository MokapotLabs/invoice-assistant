package com.mokapotlabs.invoiceassistant.workflow;

import com.mokapotlabs.invoiceassistant.app.CanonicalFilenamePolicy;
import com.mokapotlabs.invoiceassistant.domain.CompletedConfirmation;
import com.mokapotlabs.invoiceassistant.domain.ConfirmedInvoice;
import com.mokapotlabs.invoiceassistant.domain.InvoiceDocument;
import com.mokapotlabs.invoiceassistant.domain.ReviewReady;
import com.mokapotlabs.invoiceassistant.interaction.PropertyConfirmationRequest;
import com.mokapotlabs.invoiceassistant.interaction.SubmittedConfirmation;
import org.pipelineframework.awaitable.AwaitCompletionMetadata;
import org.pipelineframework.awaitable.AwaitCompletionProjector;

/** Pure Await boundary projection; the browser supplies only the property choice. */
public final class PropertyConfirmationProjector
        implements AwaitCompletionProjector<PropertyConfirmationRequest, SubmittedConfirmation, CompletedConfirmation> {
    private static final CanonicalFilenamePolicy FILENAMES = new CanonicalFilenamePolicy();

    @Override
    public CompletedConfirmation project(
            PropertyConfirmationRequest request, SubmittedConfirmation submission, AwaitCompletionMetadata metadata) {
        return confirm(request.review(), submission, metadata.completedAt());
    }

    public static CompletedConfirmation confirm(
            ReviewReady review, SubmittedConfirmation submission, java.time.Instant confirmedAt) {
        var property = review.properties().stream()
                .filter(candidate -> candidate.id().equals(submission.propertyId()))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("Choose a property from the catalogue captured for this review."));
        var reference = review.invoice();
        var document = new InvoiceDocument(
                review.documentId(),
                review.originalFilename(),
                reference.contentType() == null ? "application/pdf" : reference.contentType(),
                reference.sizeBytes(),
                reference.checksum() == null
                        ? com.mokapotlabs.invoiceassistant.domain.PayloadReferenceIdentity.of(reference)
                        : reference.checksum(),
                confirmedAt,
                reference);
        String filename = FILENAMES.filename(
                document, property, review.review(), submission.supplier(), review.filenameSeparator());
        var confirmed = new ConfirmedInvoice(
                review.documentId(),
                review.reviewId(),
                property.id(),
                property.streetNumberPrefix(),
                !review.review().recommendation().propertyId().equals(property.id()),
                !review.review().facts().supplier().equalsIgnoreCase(submission.supplier()),
                !review.review().note().equals(submission.note()),
                filename,
                confirmedAt,
                submission.supplier(),
                submission.note(),
                submission.emailRecipient(),
                submission.emailSubject(),
                submission.emailBody());
        return new CompletedConfirmation(review, confirmed);
    }
}
