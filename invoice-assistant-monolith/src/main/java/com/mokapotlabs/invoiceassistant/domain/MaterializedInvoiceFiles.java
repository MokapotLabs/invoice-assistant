package com.mokapotlabs.invoiceassistant.domain;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import org.pipelineframework.repository.PayloadReference;

/** File representation of {@link InvoiceFiles}; constructed by TPF for one step invocation. */
public record MaterializedInvoiceFiles(
        UUID documentId,
        String originalFilename,
        Path invoice,
        Path catalogue,
        PayloadReference invoiceReference,
        String invoiceSourceIdentity,
        String catalogueSourceIdentity) {
    public MaterializedInvoiceFiles {
        Objects.requireNonNull(documentId, "documentId must not be null");
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("originalFilename must not be blank");
        }
        originalFilename = originalFilename.trim();
        Objects.requireNonNull(invoice, "invoice must not be null");
        Objects.requireNonNull(catalogue, "catalogue must not be null");
        Objects.requireNonNull(invoiceReference, "invoiceReference must not be null");
        invoiceSourceIdentity = required(invoiceSourceIdentity, "invoiceSourceIdentity");
        catalogueSourceIdentity = required(catalogueSourceIdentity, "catalogueSourceIdentity");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
