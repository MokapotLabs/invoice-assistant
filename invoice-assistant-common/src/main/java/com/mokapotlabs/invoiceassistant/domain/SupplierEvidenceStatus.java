package com.mokapotlabs.invoiceassistant.domain;

/** Qualitative provenance of the supplier identity; deliberately not a numeric confidence score. */
public enum SupplierEvidenceStatus {
    EXPLICIT_TEXT,
    STRONG_TEXTUAL_IDENTITY,
    STRONG_VISUAL_IDENTITY,
    INSUFFICIENT
}
