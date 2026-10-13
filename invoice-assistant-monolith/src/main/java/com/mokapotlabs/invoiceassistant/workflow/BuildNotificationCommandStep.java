package com.mokapotlabs.invoiceassistant.workflow;

import com.mokapotlabs.invoiceassistant.domain.CompletedConfirmation;
import com.mokapotlabs.invoiceassistant.domain.InvoiceDocument;
import com.mokapotlabs.invoiceassistant.domain.NotifyAccountantCommand;
import com.mokapotlabs.invoiceassistant.domain.NotifyAttempt;
import io.quarkus.arc.Unremovable;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.cache.CacheReadBypass;
import org.pipelineframework.service.ReactiveService;

@ApplicationScoped
@Unremovable
public class BuildNotificationCommandStep
        implements ReactiveService<CompletedConfirmation, NotifyAttempt>, CacheReadBypass {
    @Override
    public Uni<NotifyAttempt> process(CompletedConfirmation confirmation) {
        return Uni.createFrom().item(() -> {
            var reviewReady = confirmation.review();
            var reference = reviewReady.invoice();
            InvoiceDocument document = new InvoiceDocument(
                    reviewReady.documentId(),
                    reviewReady.originalFilename(),
                    reference.contentType() == null ? "application/pdf" : reference.contentType(),
                    reference.sizeBytes(),
                    reference.checksum() == null
                            ? com.mokapotlabs.invoiceassistant.domain.PayloadReferenceIdentity.of(reference)
                            : reference.checksum(),
                    confirmation.confirmedInvoice().confirmedAt(),
                    reference);
            return new NotifyAttempt(new NotifyAccountantCommand(document, confirmation.confirmedInvoice()));
        });
    }
}
