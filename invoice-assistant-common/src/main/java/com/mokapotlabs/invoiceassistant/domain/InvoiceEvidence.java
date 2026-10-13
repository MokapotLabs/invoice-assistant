package com.mokapotlabs.invoiceassistant.domain;

/** Deterministic, bounded evidence supplied to invoice analysis. */
public record InvoiceEvidence(
        String text,
        String originalFilename,
        String evidenceHash,
        String extractionDiagnostic,
        AutoApprovalPolicy approvalPolicy) {
    public InvoiceEvidence(String text, String originalFilename, String evidenceHash, String extractionDiagnostic) {
        this(text, originalFilename, evidenceHash, extractionDiagnostic, AutoApprovalPolicy.manual());
    }

    public InvoiceEvidence {
        java.util.Objects.requireNonNull(approvalPolicy, "approvalPolicy must not be null");
        text = text == null ? "" : text.trim();
        originalFilename = required(originalFilename, "originalFilename");
        evidenceHash = required(evidenceHash, "evidenceHash");
        extractionDiagnostic = required(extractionDiagnostic, "extractionDiagnostic");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
