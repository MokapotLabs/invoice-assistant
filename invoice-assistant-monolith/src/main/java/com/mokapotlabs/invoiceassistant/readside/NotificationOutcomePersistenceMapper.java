package com.mokapotlabs.invoiceassistant.readside;

import com.mokapotlabs.invoiceassistant.cache.DeliveryEffectIdentity;
import com.mokapotlabs.invoiceassistant.domain.NotifyResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import org.pipelineframework.mapper.Mapper;

/** Maps a notification provider observation into its typed read-side outcome. */
@ApplicationScoped
public class NotificationOutcomePersistenceMapper implements Mapper<NotifyResult, NotificationOutcomeEntity> {
    @Override
    public NotifyResult fromExternal(NotificationOutcomeEntity external) {
        throw new UnsupportedOperationException(
                "NotificationOutcomeEntity is a query-shaped persistence representation, not a pipeline input");
    }

    @Override
    public NotificationOutcomeEntity toExternal(NotifyResult domain) {
        var command = domain.result().command();
        var provider = domain.result().providerResult();
        var entity = new NotificationOutcomeEntity();
        entity.documentId = command.document().documentId();
        entity.reviewId = command.confirmedInvoice().reviewId();
        entity.effectId = domain.result().occurrenceId();
        entity.logicalEffectId = DeliveryEffectIdentity.notification(command);
        entity.observedAt = Instant.now();
        entity.status = provider.status().name();
        entity.message = provider.message();
        entity.externalReference = provider.externalReference().orElse(null);
        return entity;
    }
}
