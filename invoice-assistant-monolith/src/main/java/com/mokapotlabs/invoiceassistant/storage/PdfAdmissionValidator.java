package com.mokapotlabs.invoiceassistant.storage;

import com.mokapotlabs.invoiceassistant.app.InvoiceAssistantConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Validates the staged controlled object; it never re-reads the browser upload. */
@ApplicationScoped
public class PdfAdmissionValidator {
    private final InvoiceAssistantConfiguration configuration;

    public PdfAdmissionValidator(InvoiceAssistantConfiguration configuration) {
        this.configuration = configuration;
    }

    public void validate(Path stagedPdf) {
        if (!configuration.pdfValidationEnabled()) return;
        try {
            Path scratch = configuration.dataRoot().resolve("tmp/pdfbox");
            Files.createDirectories(scratch);
            MemoryUsageSetting usage = MemoryUsageSetting.setupMixed(
                            8L * 1024 * 1024, configuration.maxPdfScratchBytes())
                    .setTempDir(scratch.toFile());
            try (PDDocument document = Loader.loadPDF(stagedPdf.toFile(), usage.streamCache)) {
                if (document.isEncrypted()) {
                    throw new UploadValidationException(
                            "Encrypted PDFs cannot be processed. Export an unencrypted copy.");
                }
                if (document.getNumberOfPages() < 1 || document.getNumberOfPages() > configuration.maxPdfPages()) {
                    throw new UploadValidationException("The PDF has too many pages for this invoice assistant.");
                }
            }
        } catch (UploadValidationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new UploadValidationException("The uploaded file is not a readable PDF.");
        }
    }
}
