package com.mokapotlabs.invoiceassistant.workflow;

import com.mokapotlabs.invoiceassistant.domain.DeliveryObligationResult;
import com.mokapotlabs.invoiceassistant.domain.DeliveryObligationTruth;
import com.mokapotlabs.invoiceassistant.domain.InvoiceDeliveryResult;
import com.mokapotlabs.invoiceassistant.domain.NotifyResult;
import com.mokapotlabs.invoiceassistant.storage.DeliveryOperationStatus;
import io.quarkus.arc.Unremovable;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.pipelineframework.cache.CacheReadBypass;
import org.pipelineframework.service.ReactiveService;

@ApplicationScoped
@Unremovable
public class FinalizeInvoiceDeliveryStep
        implements ReactiveService<NotifyResult, InvoiceDeliveryResult>, CacheReadBypass {
    @Override
    public Uni<InvoiceDeliveryResult> process(NotifyResult progress) {
        return Uni.createFrom().item(() -> {
            DeliveryObligationResult notification = fromAttempt(
                    progress.result().providerResult().status(),
                    progress.result().providerResult().message(),
                    progress.result().providerResult().externalReference());
            return new InvoiceDeliveryResult(
                    progress.result().command().document().documentId(),
                    progress.result().command().confirmedInvoice().reviewId(),
                    notification);
        });
    }

    private static DeliveryObligationResult fromAttempt(
            DeliveryOperationStatus status, String message, Optional<String> externalReference) {
        DeliveryObligationTruth truth =
                switch (status) {
                    case SUCCEEDED -> DeliveryObligationTruth.SUCCEEDED;
                    case FAILED -> DeliveryObligationTruth.FAILED;
                    case USER_ACTION_REQUIRED -> DeliveryObligationTruth.USER_ACTION_REQUIRED;
                    case EXTERNAL_SESSION_REQUIRED -> DeliveryObligationTruth.EXTERNAL_SESSION_REQUIRED;
                    case SUBMISSION_UNCERTAIN -> DeliveryObligationTruth.SUBMISSION_UNCERTAIN;
                    case INTERRUPTED -> DeliveryObligationTruth.INTERRUPTED;
                    case NOT_STARTED, RUNNING -> DeliveryObligationTruth.NOT_ATTEMPTED;
                };
        return new DeliveryObligationResult(truth, Optional.ofNullable(message), externalReference);
    }
}
