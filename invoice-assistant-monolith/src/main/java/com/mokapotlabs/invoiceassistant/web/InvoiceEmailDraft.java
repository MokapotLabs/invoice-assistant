package com.mokapotlabs.invoiceassistant.web;

/** Disposable UI projection of the exact email content offered for human approval. */
public record InvoiceEmailDraft(String recipient, String subject, String body) {
    public InvoiceEmailDraft {
        recipient = recipient == null ? "" : recipient.trim();
        subject = subject == null ? "" : subject.trim();
        body = body == null ? "" : body.trim();
    }

    public static InvoiceEmailDraft empty() {
        return new InvoiceEmailDraft("", "", "");
    }
}
