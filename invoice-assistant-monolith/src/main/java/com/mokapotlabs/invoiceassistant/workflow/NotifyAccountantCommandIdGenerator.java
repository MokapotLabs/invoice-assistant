package com.mokapotlabs.invoiceassistant.workflow;

import com.mokapotlabs.invoiceassistant.cache.DeliveryEffectIdentity;
import com.mokapotlabs.invoiceassistant.domain.NotifyAttempt;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.command.CommandDescriptor;
import org.pipelineframework.command.CommandIdGenerator;

@ApplicationScoped
public class NotifyAccountantCommandIdGenerator implements CommandIdGenerator<NotifyAttempt> {
    @Override
    public String commandId(CommandDescriptor descriptor, NotifyAttempt input) {
        return DeliveryEffectIdentity.notification(input.command());
    }
}
