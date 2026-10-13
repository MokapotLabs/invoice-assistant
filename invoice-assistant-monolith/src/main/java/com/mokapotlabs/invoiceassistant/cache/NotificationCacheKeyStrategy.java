package com.mokapotlabs.invoiceassistant.cache;

import com.mokapotlabs.invoiceassistant.domain.NotifyAttempt;
import com.mokapotlabs.invoiceassistant.domain.NotifyResult;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.pipelineframework.cache.CacheKeyStrategy;
import org.pipelineframework.context.PipelineContext;

/** Pre-read-safe replay identity for the accountant-notification Command boundary. */
@ApplicationScoped
@Unremovable
public final class NotificationCacheKeyStrategy implements CacheKeyStrategy {
    @Override
    public Optional<String> resolveKey(Object item, PipelineContext context) {
        if (item instanceof NotifyAttempt attempt) {
            return Optional.of(DeliveryEffectIdentity.notification(attempt.command()));
        }
        if (item instanceof NotifyResult result) {
            return Optional.of(
                    DeliveryEffectIdentity.notification(result.result().command()));
        }
        return Optional.empty();
    }

    @Override
    public boolean supportsTarget(Class<?> targetType) {
        return NotifyResult.class.equals(targetType);
    }

    @Override
    public int priority() {
        return 220;
    }
}
