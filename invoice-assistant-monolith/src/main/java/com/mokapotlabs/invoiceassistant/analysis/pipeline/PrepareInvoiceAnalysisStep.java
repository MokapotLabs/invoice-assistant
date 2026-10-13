package com.mokapotlabs.invoiceassistant.analysis.pipeline;

import com.mokapotlabs.invoiceassistant.analysis.PdfInvoiceTextExtractor;
import com.mokapotlabs.invoiceassistant.analysis.PropertyCatalogueParser;
import com.mokapotlabs.invoiceassistant.domain.InvoiceAnalysisContract;
import com.mokapotlabs.invoiceassistant.domain.InvoiceAnalysisRequest;
import com.mokapotlabs.invoiceassistant.domain.InvoiceEvidence;
import com.mokapotlabs.invoiceassistant.domain.MaterializedInvoiceFiles;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.pipelineframework.service.blocking.BlockingService;

/** Pure file transformation: declared invoice and catalogue paths in, typed analysis state out. */
@ApplicationScoped
@Unremovable
public final class PrepareInvoiceAnalysisStep
        implements BlockingService<MaterializedInvoiceFiles, InvoiceAnalysisRequest> {
    private static final int MAX_PDF_PAGES = 25;
    private static final int MAX_EXTRACTED_CHARACTERS = 120_000;
    private static final long MAX_PDF_SCRATCH_BYTES = 64L * 1024L * 1024L;

    private final PdfInvoiceTextExtractor textExtractor = new PdfInvoiceTextExtractor();
    private final PropertyCatalogueParser catalogueParser = new PropertyCatalogueParser();

    @Override
    public InvoiceAnalysisRequest processBlocking(MaterializedInvoiceFiles files) {
        return prepare(files);
    }

    InvoiceAnalysisRequest prepare(MaterializedInvoiceFiles files) {
        String text =
                textExtractor.extract(files.invoice(), MAX_PDF_PAGES, MAX_EXTRACTED_CHARACTERS, MAX_PDF_SCRATCH_BYTES);
        String catalogueHash = sha256(readAllBytes(files.catalogue(), "Property catalogue"));
        String evidenceHash = sha256((files.originalFilename() + "\n" + text).getBytes(StandardCharsets.UTF_8));
        java.util.UUID reviewId = java.util.UUID.nameUUIDFromBytes((files.documentId() + "\n" + evidenceHash + "\n"
                        + catalogueHash + "\n" + InvoiceAnalysisContract.TEXT_PROFILE)
                .getBytes(StandardCharsets.UTF_8));
        return new InvoiceAnalysisRequest(
                files.documentId(),
                reviewId,
                files.invoiceSourceIdentity(),
                files.catalogueSourceIdentity(),
                files.invoiceReference(),
                InvoiceAnalysisContract.TEXT_PROFILE,
                catalogueParser.filenameSeparator(files.catalogue()),
                catalogueHash,
                new InvoiceEvidence(
                        text,
                        files.originalFilename(),
                        evidenceHash,
                        text.isBlank()
                                ? "No extractable PDF text; supplier identity requires visual inspection."
                                : "Text extracted from searchable PDF.",
                        catalogueParser.approvalPolicy(files.catalogue())),
                catalogueParser.parse(files.catalogue()));
    }

    private static byte[] readAllBytes(java.nio.file.Path path, String label) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new IllegalArgumentException(label + " could not be read", exception);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
