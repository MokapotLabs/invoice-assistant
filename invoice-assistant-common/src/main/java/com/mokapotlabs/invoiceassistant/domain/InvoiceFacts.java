package com.mokapotlabs.invoiceassistant.domain;

import java.math.BigDecimal;

public record InvoiceFacts(
        String supplier,
        SupplierEvidenceStatus supplierEvidenceStatus,
        String invoiceNumber,
        BigDecimal totalAmount,
        String diagnostic) {
    public InvoiceFacts(String supplier, String invoiceNumber, BigDecimal totalAmount, String diagnostic) {
        this(supplier, SupplierEvidenceStatus.STRONG_TEXTUAL_IDENTITY, invoiceNumber, totalAmount, diagnostic);
    }

    public InvoiceFacts {
        if (supplierEvidenceStatus == null) {
            supplierEvidenceStatus = SupplierEvidenceStatus.INSUFFICIENT;
        }
        if (supplier == null
                || supplier.isBlank()
                || invoiceNumber == null
                || invoiceNumber.isBlank()
                || totalAmount == null
                || diagnostic == null
                || diagnostic.isBlank()) {
            throw new IllegalArgumentException(
                    "invoice facts require a supplier, supplier evidence status, invoice number, total amount and diagnostic");
        }
        supplier = supplier.trim();
        invoiceNumber = invoiceNumber.trim();
        if (totalAmount.signum() < 0) {
            throw new IllegalArgumentException("invoice total must not be negative");
        }
        diagnostic = diagnostic.trim();
    }
}
