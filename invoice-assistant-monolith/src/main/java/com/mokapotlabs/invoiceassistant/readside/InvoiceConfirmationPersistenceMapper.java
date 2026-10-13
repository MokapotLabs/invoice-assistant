package com.mokapotlabs.invoiceassistant.readside;

import com.mokapotlabs.invoiceassistant.domain.CompletedConfirmation;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.mapper.Mapper;

/** Flattens the Await result into a directly queryable confirmation audit row. */
@ApplicationScoped
public class InvoiceConfirmationPersistenceMapper implements Mapper<CompletedConfirmation, InvoiceConfirmationEntity> {
    @Override
    public CompletedConfirmation fromExternal(InvoiceConfirmationEntity external) {
        throw new UnsupportedOperationException(
                "InvoiceConfirmationEntity is a query-shaped persistence representation, not a pipeline input");
    }

    @Override
    public InvoiceConfirmationEntity toExternal(CompletedConfirmation domain) {
        var entity = new InvoiceConfirmationEntity();
        var confirmed = domain.confirmedInvoice();
        entity.reviewId = domain.review().reviewId();
        entity.documentId = domain.review().documentId();
        entity.confirmedAt = confirmed.confirmedAt();
        entity.recommendedPropertyId = domain.review().review().recommendation().propertyId();
        entity.confirmedPropertyId = confirmed.confirmedPropertyId();
        entity.propertyStreetNumber = confirmed.propertyStreetNumber();
        entity.canonicalFilename = confirmed.canonicalFilename();
        entity.recommendedSupplier = domain.review().review().facts().supplier();
        entity.confirmedSupplier = confirmed.supplier();
        entity.supplierCorrected = !entity.recommendedSupplier.equals(entity.confirmedSupplier);
        entity.recommendedNote = domain.review().review().note();
        entity.confirmedNote = confirmed.note();
        entity.noteCorrected = !entity.recommendedNote.equals(entity.confirmedNote);
        entity.emailRecipient = confirmed.emailRecipient();
        entity.emailSubject = confirmed.emailSubject();
        entity.emailBody = confirmed.emailBody();
        return entity;
    }
}
