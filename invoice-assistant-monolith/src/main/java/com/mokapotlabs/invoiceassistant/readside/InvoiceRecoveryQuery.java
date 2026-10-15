package com.mokapotlabs.invoiceassistant.readside;

import com.mokapotlabs.invoiceassistant.analysis.PropertyCatalogueParser;
import com.mokapotlabs.invoiceassistant.domain.ReviewReady;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/** Rehydrates an attended review from persisted business facts and its original catalogue snapshot. */
@ApplicationScoped
public class InvoiceRecoveryQuery {
    private final InvoiceAdmissionQuery admissions;
    private final InvoiceReviewQuery reviews;
    private final InvoiceReviewPersistenceMapper mapper;

    public InvoiceRecoveryQuery(
            InvoiceAdmissionQuery admissions, InvoiceReviewQuery reviews, InvoiceReviewPersistenceMapper mapper) {
        this.admissions = admissions;
        this.reviews = reviews;
        this.mapper = mapper;
    }

    public Optional<ReviewReady> review(UUID documentId) {
        return reviews.latest(documentId).map(review -> {
            var files = admissions.files(admissions.find(documentId).orElseThrow());
            var reference = files.catalogue();
            if (!"filesystem".equals(reference.provider())) {
                throw new IllegalStateException("The saved catalogue is not a local filesystem payload.");
            }
            Path root = Path.of(reference.container()).toAbsolutePath().normalize();
            Path catalogue = root.resolve(reference.key()).normalize();
            try {
                if (!catalogue.startsWith(root)
                        || !Files.isRegularFile(catalogue)
                        || Files.size(catalogue) > 2_000_000) {
                    throw new IllegalStateException("The original review catalogue is unavailable.");
                }
                String checksum = HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(catalogue)));
                if (!checksum.equals(reference.checksum()) || !checksum.equals(review.catalogueHash)) {
                    throw new IllegalStateException("The original review catalogue has changed.");
                }
                var parser = new PropertyCatalogueParser();
                // Recovery is attended: never reapply an old opt-in auto-approval preference.
                return new ReviewReady(
                        review.reviewId,
                        documentId,
                        files.invoice(),
                        review.originalFilename,
                        review.evidenceHash,
                        review.catalogueHash,
                        review.analysisProfile,
                        parser.filenameSeparator(catalogue),
                        parser.parse(catalogue),
                        mapper.review(review));
            } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
                throw new IllegalStateException("The original review catalogue could not be read.", failure);
            }
        });
    }
}
