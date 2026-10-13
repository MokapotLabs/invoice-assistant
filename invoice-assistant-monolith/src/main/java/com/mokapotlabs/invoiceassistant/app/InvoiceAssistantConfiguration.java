package com.mokapotlabs.invoiceassistant.app;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.Objects;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class InvoiceAssistantConfiguration {
    private final Path dataRoot;
    private final long maxUploadBytes;
    private final Path settingsPath;
    private final int maxPdfPages;
    private final int maxExtractedCharacters;
    private final long maxPdfScratchBytes;
    private final boolean pdfValidationEnabled;

    @Inject
    public InvoiceAssistantConfiguration(
            @ConfigProperty(name = "invoice-assistant.data-root") Path dataRoot,
            @ConfigProperty(name = "invoice-assistant.max-upload-bytes") long maxUploadBytes,
            @ConfigProperty(name = "invoice-assistant.pdf.max-pages", defaultValue = "25") int maxPdfPages,
            @ConfigProperty(name = "invoice-assistant.pdf.max-extracted-characters", defaultValue = "250000")
                    int maxExtractedCharacters,
            @ConfigProperty(name = "invoice-assistant.pdf.max-scratch-bytes", defaultValue = "67108864")
                    long maxPdfScratchBytes,
            @ConfigProperty(name = "invoice-assistant.pdf.validation-enabled", defaultValue = "true")
                    boolean pdfValidationEnabled) {
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot must not be null")
                .toAbsolutePath()
                .normalize();
        if (maxUploadBytes <= 0 || maxPdfPages < 1 || maxExtractedCharacters < 1 || maxPdfScratchBytes < 1) {
            throw new IllegalArgumentException("upload and PDF limits must be positive");
        }
        this.maxUploadBytes = maxUploadBytes;
        this.maxPdfPages = maxPdfPages;
        this.maxExtractedCharacters = maxExtractedCharacters;
        this.maxPdfScratchBytes = maxPdfScratchBytes;
        this.pdfValidationEnabled = pdfValidationEnabled;
        this.settingsPath = this.dataRoot.resolve("config/settings.yaml");
    }

    public InvoiceAssistantConfiguration(Path dataRoot, long maxUploadBytes) {
        this(dataRoot, maxUploadBytes, 25, 250000, 67108864, false);
    }

    public Path dataRoot() {
        return dataRoot;
    }

    public long maxUploadBytes() {
        return maxUploadBytes;
    }

    public Path settingsPath() {
        return settingsPath;
    }

    public int maxPdfPages() {
        return maxPdfPages;
    }

    public int maxExtractedCharacters() {
        return maxExtractedCharacters;
    }

    public long maxPdfScratchBytes() {
        return maxPdfScratchBytes;
    }

    public boolean pdfValidationEnabled() {
        return pdfValidationEnabled;
    }
}
