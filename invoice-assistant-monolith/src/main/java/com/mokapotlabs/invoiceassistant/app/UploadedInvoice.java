package com.mokapotlabs.invoiceassistant.app;

import java.util.Objects;
import org.pipelineframework.repository.PayloadReference;

/** Admission metadata for a PDF already committed by the generated HTTP boundary. */
public record UploadedInvoice(PayloadReference invoice, String originalFilename) {
    public UploadedInvoice {
        Objects.requireNonNull(invoice, "invoice");
        Objects.requireNonNull(originalFilename, "originalFilename");
    }
}
