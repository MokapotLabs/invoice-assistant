package com.mokapotlabs.invoiceassistant.app;

import com.mokapotlabs.invoiceassistant.domain.InvoiceDocument;
import com.mokapotlabs.invoiceassistant.domain.InvoiceFiles;

public sealed interface UploadResult
        permits UploadResult.Accepted, UploadResult.Duplicate, UploadResult.Rejected, UploadResult.Failed {
    record Accepted(InvoiceDocument document, InvoiceFiles files) implements UploadResult {}

    record Duplicate(InvoiceDocument existingDocument) implements UploadResult {}

    record Rejected(String message) implements UploadResult {}

    record Failed(String message) implements UploadResult {}
}
