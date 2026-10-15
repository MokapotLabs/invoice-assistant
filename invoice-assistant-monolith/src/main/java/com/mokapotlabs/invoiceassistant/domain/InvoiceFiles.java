package com.mokapotlabs.invoiceassistant.domain;

import java.util.Objects;
import java.util.UUID;
import org.pipelineframework.repository.PayloadReference;

/** Canonical admission fact for one invoice and the catalogue snapshot analysed with it. */
public record InvoiceFiles(
        UUID documentId,
        String originalFilename,
        PayloadReference invoice,
        PayloadReference catalogue,
        PayloadReference invoiceReference,
        String invoiceSourceIdentity,
        String catalogueSourceIdentity)
        implements InvoiceEntry {
    public InvoiceFiles(
            UUID documentId, String originalFilename, PayloadReference invoice, PayloadReference catalogue) {
        this(
                documentId,
                originalFilename,
                invoice,
                catalogue,
                invoice,
                PayloadReferenceIdentity.of(invoice),
                PayloadReferenceIdentity.of(catalogue));
    }

    public InvoiceFiles {
        Objects.requireNonNull(documentId, "documentId must not be null");
        originalFilename = required(originalFilename, "originalFilename");
        Objects.requireNonNull(invoice, "invoice must not be null");
        Objects.requireNonNull(catalogue, "catalogue must not be null");
        Objects.requireNonNull(invoiceReference, "invoiceReference must not be null");
        if (!invoice.equals(invoiceReference)) {
            throw new IllegalArgumentException("invoiceReference must identify the admitted invoice");
        }
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
