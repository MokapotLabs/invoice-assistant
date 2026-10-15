package com.mokapotlabs.invoiceassistant.domain;

/** Trusted admissions at the original invoice, saved review, or approved delivery boundary. */
public sealed interface InvoiceEntry permits InvoiceFiles, ReviewReady, NotifyAttempt {}
