package com.mokapotlabs.invoiceassistant.analysis.pipeline;

import com.mokapotlabs.invoiceassistant.domain.InvoiceFacts;
import com.mokapotlabs.invoiceassistant.domain.InvoiceReview;
import com.mokapotlabs.invoiceassistant.domain.ReviewReady;
import com.mokapotlabs.invoiceassistant.domain.SupplierEvidenceStatus;
import com.mokapotlabs.invoiceassistant.domain.VisionAnalysisResult;
import com.mokapotlabs.invoiceassistant.domain.VisionSupplierObservation;
import io.quarkus.arc.Unremovable;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.service.ReactiveService;

/** Pure merge of the narrow visual supplier observation into the existing text review. */
@ApplicationScoped
@Unremovable
public final class MergeVisionSupplierEvidenceStep implements ReactiveService<VisionAnalysisResult, ReviewReady> {
    @Override
    public Uni<ReviewReady> process(VisionAnalysisResult result) {
        return Uni.createFrom().item(merge(result));
    }

    ReviewReady merge(VisionAnalysisResult result) {
        InvoiceReview textReview = result.textReview();
        InvoiceFacts textFacts = textReview.facts();
        InvoiceFacts mergedFacts = new InvoiceFacts(
                result.observation().supplier(),
                result.observation().supplierEvidenceStatus(),
                textFacts.invoiceNumber(),
                textFacts.totalAmount(),
                mergeDiagnostic(textFacts.diagnostic(), result.observation()));
        InvoiceReview mergedReview = new InvoiceReview(mergedFacts, textReview.recommendation(), textReview.note());
        return new ReviewReady(
                result.reviewId(),
                result.documentId(),
                result.invoice(),
                result.originalFilename(),
                result.evidenceHash(),
                result.catalogueHash(),
                result.analysisProfile(),
                result.filenameSeparator(),
                result.properties(),
                mergedReview);
    }

    private static String mergeDiagnostic(String textDiagnostic, VisionSupplierObservation observation) {
        String visionDiagnostic = observation.supplierEvidenceStatus() == SupplierEvidenceStatus.STRONG_VISUAL_IDENTITY
                ? "Visual supplier identity resolved as " + observation.supplier() + "."
                : "Visual supplier identity remained unresolved.";
        if (textDiagnostic.equals(visionDiagnostic)) {
            return textDiagnostic;
        }
        return textDiagnostic + " Vision: " + visionDiagnostic;
    }
}
