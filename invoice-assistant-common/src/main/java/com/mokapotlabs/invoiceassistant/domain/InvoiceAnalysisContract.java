package com.mokapotlabs.invoiceassistant.domain;

/** Versioned semantic identity of the instructions, evidence preparation, and completion schema. */
public final class InvoiceAnalysisContract {
    public static final String TEXT_PROFILE = "invoice-text-analysis:v4|InvoiceReview:v3";
    public static final String VISION_PROFILE = "invoice-vision-analysis:v1|InvoiceReview:v3|header-raster:v1";

    private InvoiceAnalysisContract() {}
}
