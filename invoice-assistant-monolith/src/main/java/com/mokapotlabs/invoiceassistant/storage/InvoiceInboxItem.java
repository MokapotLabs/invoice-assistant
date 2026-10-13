package com.mokapotlabs.invoiceassistant.storage;

import com.mokapotlabs.invoiceassistant.domain.InvoiceDocument;

public record InvoiceInboxItem(
        InvoiceDocument document,
        InvoiceReviewState reviewState,
        DeliveryState deliveryState,
        boolean available,
        String availabilityMessage) {}
