package com.mokapotlabs.invoiceassistant.readside;

import com.mokapotlabs.invoiceassistant.domain.InvoiceDeliveryResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import org.pipelineframework.mapper.Mapper;

/** Maps the terminal workflow value into one directly queryable delivery row. */
@ApplicationScoped
public class InvoiceDeliveryPersistenceMapper implements Mapper<InvoiceDeliveryResult, InvoiceDeliveryEntity> {
    @Override
    public InvoiceDeliveryResult fromExternal(InvoiceDeliveryEntity external) {
        throw new UnsupportedOperationException(
                "InvoiceDeliveryEntity is a query-shaped persistence representation, not a pipeline input");
    }

    @Override
    public InvoiceDeliveryEntity toExternal(InvoiceDeliveryResult domain) {
        var entity = new InvoiceDeliveryEntity();
        entity.reviewId = domain.reviewId();
        entity.documentId = domain.documentId();
        entity.observedAt = Instant.now();
        entity.notificationTruth = domain.notification().truth().name();
        entity.notificationMessage = domain.notification().message().orElse(null);
        entity.notificationExternalReference =
                domain.notification().externalReference().orElse(null);
        return entity;
    }
}
