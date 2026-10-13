package com.mokapotlabs.invoiceassistant.readside;

import com.mokapotlabs.invoiceassistant.domain.InvoiceFacts;
import com.mokapotlabs.invoiceassistant.domain.InvoiceReview;
import com.mokapotlabs.invoiceassistant.domain.PropertyRecommendation;
import com.mokapotlabs.invoiceassistant.domain.ReviewReady;
import com.mokapotlabs.invoiceassistant.domain.SupplierEvidenceStatus;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import org.pipelineframework.mapper.Mapper;

/** Flattens ReviewReady into the typed review read model persisted by TPF. */
@ApplicationScoped
public class InvoiceReviewPersistenceMapper implements Mapper<ReviewReady, InvoiceReviewEntity> {
    @Override
    public ReviewReady fromExternal(InvoiceReviewEntity external) {
        throw new UnsupportedOperationException(
                "InvoiceReviewEntity is a query-shaped persistence representation, not a pipeline input");
    }

    @Override
    public InvoiceReviewEntity toExternal(ReviewReady domain) {
        InvoiceFacts facts = domain.review().facts();
        PropertyRecommendation recommendation = domain.review().recommendation();
        var entity = new InvoiceReviewEntity();
        entity.reviewId = domain.reviewId();
        entity.documentId = domain.documentId();
        entity.observedAt = Instant.now();
        entity.originalFilename = domain.originalFilename();
        entity.evidenceHash = domain.evidenceHash();
        entity.catalogueHash = domain.catalogueHash();
        entity.analysisProfile = domain.analysisProfile();
        entity.supplier = facts.supplier();
        entity.supplierEvidenceStatus = facts.supplierEvidenceStatus().name();
        entity.invoiceNumber = facts.invoiceNumber();
        entity.totalAmount = facts.totalAmount();
        entity.diagnostic = facts.diagnostic();
        entity.recommendedPropertyId = recommendation.propertyId();
        entity.recommendationExplanation = recommendation.explanation();
        entity.note = domain.review().note();
        return entity;
    }

    public InvoiceReview review(InvoiceReviewEntity entity) {
        return new InvoiceReview(
                new InvoiceFacts(
                        entity.supplier,
                        SupplierEvidenceStatus.valueOf(entity.supplierEvidenceStatus),
                        entity.invoiceNumber,
                        entity.totalAmount,
                        entity.diagnostic),
                new PropertyRecommendation(entity.recommendedPropertyId, entity.recommendationExplanation),
                entity.note);
    }
}
