package com.mokapotlabs.invoiceassistant.delivery;

import com.mokapotlabs.invoiceassistant.app.NotifyAccountantResult;
import com.mokapotlabs.invoiceassistant.domain.ConfirmedInvoice;
import com.mokapotlabs.invoiceassistant.domain.InvoiceDocument;
import com.mokapotlabs.invoiceassistant.storage.DeliveryOperationStatus;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class FakeAccountantNotification {
    private final Set<java.util.UUID> failedOnce = ConcurrentHashMap.newKeySet();

    public NotifyAccountantResult notifyAccountant(InvoiceDocument document, ConfirmedInvoice confirmedInvoice) {
        String filename = document.originalFilename().toLowerCase(Locale.ROOT);
        if (filename.contains("notification-user-action")) {
            return new NotifyAccountantResult(
                    DeliveryOperationStatus.USER_ACTION_REQUIRED,
                    "Confirm the accountant recipient before retrying attachment delivery.",
                    Optional.empty());
        }
        if (filename.contains("notification-session")) {
            return new NotifyAccountantResult(
                    DeliveryOperationStatus.EXTERNAL_SESSION_REQUIRED,
                    "An accountant mail session is required before the PDF attachment can be sent.",
                    Optional.empty());
        }
        if (filename.contains("notification-fail-once") && failedOnce.add(document.documentId())) {
            return new NotifyAccountantResult(
                    DeliveryOperationStatus.FAILED,
                    "Fake accountant delivery failed on its first attempt.",
                    Optional.empty());
        }
        if (filename.contains("notification-fail") && !filename.contains("notification-fail-once")) {
            return new NotifyAccountantResult(
                    DeliveryOperationStatus.FAILED, "Fake accountant delivery reported a failure.", Optional.empty());
        }
        return new NotifyAccountantResult(
                DeliveryOperationStatus.SUCCEEDED,
                "Fake accountant delivery accepted the PDF attachment.",
                Optional.of("fake-notification-" + document.documentId()));
    }
}
