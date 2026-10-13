package com.mokapotlabs.invoiceassistant.cache;

import com.mokapotlabs.invoiceassistant.domain.InvoiceAnalysisRequest;
import com.mokapotlabs.invoiceassistant.domain.InvoiceFiles;
import com.mokapotlabs.invoiceassistant.domain.PayloadReferenceIdentity;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.pipelineframework.cache.CacheKeyStrategy;
import org.pipelineframework.context.PipelineContext;

/** Pre-read-safe cache identity for the file preparation boundary. */
@ApplicationScoped
@Unremovable
public final class PreparationCacheKeyStrategy implements CacheKeyStrategy {
    private static final String PREFIX = "prepare-invoice:v1:";

    @Override
    public Optional<String> resolveKey(Object item, PipelineContext context) {
        if (item instanceof InvoiceFiles files) {
            return Optional.of(key(files.invoiceSourceIdentity(), files.catalogueSourceIdentity()));
        }
        if (item instanceof InvoiceAnalysisRequest request) {
            return Optional.of(key(request.invoiceSourceIdentity(), request.catalogueSourceIdentity()));
        }
        return Optional.empty();
    }

    @Override
    public boolean supportsTarget(Class<?> targetType) {
        return InvoiceAnalysisRequest.class.equals(targetType);
    }

    @Override
    public int priority() {
        return 200;
    }

    private static String key(String invoiceIdentity, String catalogueIdentity) {
        return PREFIX + PayloadReferenceIdentity.sha256(invoiceIdentity + "\n" + catalogueIdentity);
    }
}
