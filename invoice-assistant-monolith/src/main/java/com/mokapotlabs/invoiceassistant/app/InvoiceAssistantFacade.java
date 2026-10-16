package com.mokapotlabs.invoiceassistant.app;

import com.mokapotlabs.invoiceassistant.analysis.PropertyCatalog;
import com.mokapotlabs.invoiceassistant.analysis.TpfInvoiceAnalysisWorkflow;
import com.mokapotlabs.invoiceassistant.domain.Property;
import com.mokapotlabs.invoiceassistant.readside.InvoiceReadModel;
import com.mokapotlabs.invoiceassistant.readside.InvoiceReadSide;
import com.mokapotlabs.invoiceassistant.storage.FilesystemInvoiceDocumentStore;
import com.mokapotlabs.invoiceassistant.storage.InvoiceInbox;
import com.mokapotlabs.invoiceassistant.storage.InvoiceInboxService;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class InvoiceAssistantFacade {
    private final FilesystemInvoiceDocumentStore documentStore;
    private final InvoiceInboxService inboxService;
    private final TpfInvoiceAnalysisWorkflow workflow;
    private final InvoiceReadSide readSide;
    private final PropertyCatalog propertyCatalog;

    public InvoiceAssistantFacade(
            FilesystemInvoiceDocumentStore documentStore,
            InvoiceInboxService inboxService,
            TpfInvoiceAnalysisWorkflow workflow,
            InvoiceReadSide readSide,
            PropertyCatalog propertyCatalog) {
        this.documentStore = documentStore;
        this.inboxService = inboxService;
        this.workflow = workflow;
        this.readSide = readSide;
        this.propertyCatalog = propertyCatalog;
    }

    public UploadResult admitInvoice(UploadedInvoice upload, String principal) {
        UploadResult result = documentStore.admit(upload, principal);
        if (result instanceof UploadResult.Accepted accepted) workflow.process(accepted.files());
        return result;
    }

    public InvoiceInbox inbox() {
        return inboxService.inbox();
    }

    public void dismissFromInbox(UUID documentId) {
        inboxService.dismiss(documentId);
    }

    public Optional<InvoiceReadModel> review(UUID documentId) {
        return readSide.find(documentId);
    }

    public List<Property> properties() {
        return propertyCatalog.properties();
    }

    public ConfirmationResult confirmProperty(
            UUID documentId,
            String propertyId,
            String supplier,
            String note,
            String emailRecipient,
            String emailSubject,
            String emailBody) {
        // A repeated form submission must lead back to delivery, never admit another effect.
        if (review(documentId)
                .flatMap(record -> record.reviewState().confirmation())
                .isPresent()) {
            return new ConfirmationResult.Accepted();
        }
        if (propertyId == null || propertyId.isBlank()) {
            return new ConfirmationResult.Rejected("Choose a configured property before confirming.");
        }
        if (supplier == null || supplier.isBlank()) {
            return new ConfirmationResult.Rejected("Enter the supplier before confirming.");
        }
        if (emailRecipient == null
                || !emailRecipient.contains("@")
                || emailRecipient.startsWith("@")
                || emailRecipient.endsWith("@")) {
            return new ConfirmationResult.Rejected("Enter a valid recipient email address before confirming.");
        }
        if (emailSubject == null || emailSubject.isBlank()) {
            return new ConfirmationResult.Rejected("Enter an email subject before confirming.");
        }
        if (emailBody == null || emailBody.isBlank()) {
            return new ConfirmationResult.Rejected("Enter an email body before confirming.");
        }
        String confirmedNote = note == null ? "" : note.trim();
        try {
            if (!workflow.completeConfirmation(
                    documentId, propertyId, supplier, confirmedNote, emailRecipient, emailSubject, emailBody)) {
                return new ConfirmationResult.Rejected("This invoice is not waiting for property confirmation.");
            }
            return new ConfirmationResult.Accepted();
        } catch (IllegalArgumentException exception) {
            return new ConfirmationResult.Rejected(exception.getMessage());
        }
    }

    public DeliveryRetryResult retryDelivery(UUID documentId) {
        return workflow.retryDelivery(documentId);
    }

    public DeliveryRetryResult retryDelivery(UUID documentId, boolean acknowledgePossibleDuplicate) {
        return workflow.retryDelivery(documentId, acknowledgePossibleDuplicate);
    }

    public DeliveryRetryResult redriveAnalysis(UUID documentId) {
        return workflow.redriveAnalysis(documentId);
    }

    public boolean canReissueNotification(UUID documentId) {
        return workflow.canReissueNotification(documentId);
    }

    public DeliveryRetryResult reissueNotification(UUID documentId, String reason) {
        return workflow.reissueNotification(documentId, reason);
    }
}
