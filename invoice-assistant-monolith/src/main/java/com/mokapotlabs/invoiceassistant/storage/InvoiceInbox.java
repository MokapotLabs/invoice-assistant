package com.mokapotlabs.invoiceassistant.storage;

import java.util.List;

public record InvoiceInbox(List<InvoiceInboxItem> documents, List<RegistryLoadIssue> issues) {
    public InvoiceInbox {
        documents = List.copyOf(documents);
        issues = List.copyOf(issues);
    }
}
