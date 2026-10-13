package com.mokapotlabs.invoiceassistant.cache;

import com.mokapotlabs.invoiceassistant.domain.InvoiceAnalysisRequest;
import com.mokapotlabs.invoiceassistant.domain.PayloadReferenceIdentity;
import com.mokapotlabs.invoiceassistant.domain.ReviewReady;
import com.mokapotlabs.invoiceassistant.domain.TextAnalysisResult;
import com.mokapotlabs.invoiceassistant.domain.VisionAnalysisRequest;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.pipelineframework.cache.CacheKeyStrategy;
import org.pipelineframework.context.PipelineContext;

/** Semantic result-replay identity for the one-turn invoice analysis boundary. */
@ApplicationScoped
@Unremovable
public final class AnalysisCacheKeyStrategy implements CacheKeyStrategy {
    private static final String TEXT_PREFIX = "analyse-invoice-text:v2:";
    private static final String VISION_PREFIX = "analyse-invoice-vision:v1:";

    @Override
    public Optional<String> resolveKey(Object item, PipelineContext context) {
        if (item instanceof InvoiceAnalysisRequest request) {
            return Optional.of(textKey(
                    request.documentId().toString(),
                    request.evidence().evidenceHash(),
                    request.catalogueHash(),
                    request.analysisProfile()));
        }
        if (item instanceof TextAnalysisResult result) {
            return Optional.of(textKey(
                    result.documentId().toString(),
                    result.evidence().evidenceHash(),
                    result.catalogueHash(),
                    result.analysisProfile()));
        }
        if (item instanceof VisionAnalysisRequest request) {
            return Optional.of(visionKey(
                    request.documentId().toString(),
                    request.evidence().evidenceHash(),
                    request.catalogueHash(),
                    request.visionProfile(),
                    PayloadReferenceIdentity.of(request.image())));
        }
        if (item instanceof ReviewReady ready) {
            String prefix = ready.analysisProfile().contains("vision") ? VISION_PREFIX : TEXT_PREFIX;
            return Optional.of(prefix
                    + PayloadReferenceIdentity.sha256(ready.documentId() + "\n" + ready.evidenceHash() + "\n"
                            + ready.catalogueHash() + "\n" + ready.analysisProfile()));
        }
        return Optional.empty();
    }

    @Override
    public boolean supportsTarget(Class<?> targetType) {
        return TextAnalysisResult.class.equals(targetType) || ReviewReady.class.equals(targetType);
    }

    @Override
    public int priority() {
        return 250;
    }

    private static String textKey(
            String documentId, String evidenceHash, String catalogueHash, String analysisProfile) {
        return TEXT_PREFIX
                + PayloadReferenceIdentity.sha256(
                        documentId + "\n" + evidenceHash + "\n" + catalogueHash + "\n" + analysisProfile);
    }

    private static String visionKey(
            String documentId, String evidenceHash, String catalogueHash, String visionProfile, String imageIdentity) {
        return VISION_PREFIX
                + PayloadReferenceIdentity.sha256(documentId + "\n" + evidenceHash + "\n" + catalogueHash + "\n"
                        + visionProfile + "\n" + imageIdentity);
    }
}
