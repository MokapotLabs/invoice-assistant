package com.mokapotlabs.invoiceassistant.workflow;

import com.mokapotlabs.invoiceassistant.app.CanonicalFilenamePolicy;
import com.mokapotlabs.invoiceassistant.domain.AutoApprovalPolicy;
import com.mokapotlabs.invoiceassistant.domain.InvoiceApprovalRoute;
import com.mokapotlabs.invoiceassistant.domain.InvoiceDocument;
import com.mokapotlabs.invoiceassistant.domain.InvoiceEvidence;
import com.mokapotlabs.invoiceassistant.domain.InvoiceFacts;
import com.mokapotlabs.invoiceassistant.domain.InvoiceReview;
import com.mokapotlabs.invoiceassistant.domain.Property;
import com.mokapotlabs.invoiceassistant.domain.ReviewReady;
import com.mokapotlabs.invoiceassistant.domain.SupplierEvidenceStatus;
import com.mokapotlabs.invoiceassistant.interaction.SubmittedConfirmation;
import io.quarkus.arc.Unremovable;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Locale;
import java.util.stream.Stream;
import org.pipelineframework.service.ReactiveService;

/** Pure approval policy: deterministic evidence and captured delivery authority, or human review. */
@ApplicationScoped
@Unremovable
public class DecideInvoiceApprovalStep implements ReactiveService<ReviewReady, InvoiceApprovalRoute> {
    @Override
    public Uni<InvoiceApprovalRoute> process(ReviewReady review) {
        return Uni.createFrom().item(() -> decide(review));
    }

    InvoiceApprovalRoute decide(ReviewReady review) {
        if (review.evidence().isEmpty()) return review;
        InvoiceEvidence evidence = review.evidence().orElseThrow();
        AutoApprovalPolicy policy = evidence.approvalPolicy();
        InvoiceFacts facts = review.review().facts();
        if (!policy.enabled()
                || policy.subjectTemplate().isBlank()
                || policy.bodyTemplate().isBlank()
                || (facts.supplierEvidenceStatus() != SupplierEvidenceStatus.EXPLICIT_TEXT
                        && facts.supplierEvidenceStatus() != SupplierEvidenceStatus.STRONG_TEXTUAL_IDENTITY)
                || facts.totalAmount().signum() <= 0
                || !facts.diagnostic().equalsIgnoreCase("No extraction warnings.")
                || !contains(evidence.text(), facts.supplier())
                || !contains(evidence.text(), facts.invoiceNumber())
                || !contains(evidence.text(), facts.totalAmount().toPlainString())) return review;
        var matches = review.properties().stream()
                .filter(property -> Stream.concat(Stream.of(property.canonicalAddress()), property.aliases().stream())
                        .filter(address ->
                                address.length() >= 8 && address.chars().anyMatch(Character::isDigit))
                        .anyMatch(address -> contains(evidence.text(), address)))
                .toList();
        if (matches.size() != 1) return review;
        Property property = matches.getFirst();
        if (!property.id().equals(review.review().recommendation().propertyId())) return review;
        var delivery = policy.deliveries().stream()
                .filter(item -> item.propertyId().equals(property.id())
                        && item.ready()
                        && item.recipient().contains("@")
                        && !item.recipient().endsWith(".invalid"))
                .findFirst();
        if (delivery.isEmpty()) return review;
        var reference = review.invoice();
        var document = new InvoiceDocument(
                review.documentId(),
                review.originalFilename(),
                reference.contentType(),
                reference.sizeBytes(),
                reference.checksum(),
                policy.admittedAt(),
                reference);
        String filename = new CanonicalFilenamePolicy()
                .filename(document, property, review.review(), facts.supplier(), review.filenameSeparator());
        var submission = new SubmittedConfirmation(
                property.id(),
                facts.supplier(),
                review.review().note(),
                delivery.orElseThrow().recipient(),
                render(policy.subjectTemplate(), property, filename, review.review()),
                render(policy.bodyTemplate(), property, filename, review.review()));
        return PropertyConfirmationProjector.confirm(review, submission, policy.admittedAt());
    }

    private static String render(String template, Property property, String filename, InvoiceReview review) {
        return template.replace("{property}", property.id())
                .replace("{property-street-number}", property.streetNumberPrefix())
                .replace("{filename}", filename)
                .replace("{supplier}", review.facts().supplier())
                .replace("{note}", review.note());
    }

    private static boolean contains(String text, String fact) {
        String value = normalize(fact);
        return !value.isBlank() && (" " + normalize(text) + " ").contains(" " + value + " ");
    }

    private static String normalize(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }
}
