package com.mokapotlabs.invoiceassistant.workflow;

import com.mokapotlabs.invoiceassistant.domain.ReviewReady;
import com.mokapotlabs.invoiceassistant.interaction.PropertyConfirmationRequest;
import io.quarkus.arc.Unremovable;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.service.ReactiveService;

/** Initiates confirmation; the framework owns suspension and completion. */
@ApplicationScoped
@Unremovable
public class BeginPropertyConfirmationStep implements ReactiveService<ReviewReady, PropertyConfirmationRequest> {
    @Override
    public Uni<PropertyConfirmationRequest> process(ReviewReady review) {
        return Uni.createFrom().item(new PropertyConfirmationRequest(review));
    }
}
