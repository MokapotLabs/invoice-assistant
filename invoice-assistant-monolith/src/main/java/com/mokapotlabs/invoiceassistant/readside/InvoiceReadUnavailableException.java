package com.mokapotlabs.invoiceassistant.readside;

import java.io.IOException;

/** Distinguishes a failed read-side authority from an absent invoice. */
public final class InvoiceReadUnavailableException extends RuntimeException {
    public InvoiceReadUnavailableException(IOException cause) {
        super("Invoice history is temporarily unavailable", cause);
    }
}
