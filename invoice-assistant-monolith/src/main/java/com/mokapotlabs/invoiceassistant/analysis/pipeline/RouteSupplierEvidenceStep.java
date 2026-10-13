package com.mokapotlabs.invoiceassistant.analysis.pipeline;

import com.mokapotlabs.invoiceassistant.domain.AnalysisRoute;
import com.mokapotlabs.invoiceassistant.domain.InvoiceAnalysisContract;
import com.mokapotlabs.invoiceassistant.domain.ReviewReady;
import com.mokapotlabs.invoiceassistant.domain.SupplierEvidenceStatus;
import com.mokapotlabs.invoiceassistant.domain.TextAnalysisResult;
import com.mokapotlabs.invoiceassistant.domain.VisionRasterizationRequest;
import io.quarkus.arc.Unremovable;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.service.ReactiveService;

/** Pure qualitative evidence router; it is the only gate that can activate vision. */
@ApplicationScoped
@Unremovable
public final class RouteSupplierEvidenceStep implements ReactiveService<TextAnalysisResult, AnalysisRoute> {
    @Override
    public Uni<AnalysisRoute> process(TextAnalysisResult result) {
        return Uni.createFrom().item(route(result));
    }

    AnalysisRoute route(TextAnalysisResult result) {
        if (result.review().facts().supplierEvidenceStatus() != SupplierEvidenceStatus.INSUFFICIENT) {
            return new ReviewReady(
                    result.reviewId(),
                    result.documentId(),
                    result.invoice(),
                    result.evidence().originalFilename(),
                    result.evidence().evidenceHash(),
                    result.catalogueHash(),
                    result.analysisProfile(),
                    result.filenameSeparator(),
                    result.properties(),
                    result.review(),
                    java.util.Optional.of(result.evidence()));
        }
        return new VisionRasterizationRequest(
                result.documentId(),
                result.reviewId(),
                result.invoice(),
                result.analysisProfile(),
                InvoiceAnalysisContract.VISION_PROFILE,
                result.filenameSeparator(),
                result.catalogueHash(),
                result.evidence(),
                result.properties(),
                result.review());
    }
}
