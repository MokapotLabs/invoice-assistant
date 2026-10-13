package com.mokapotlabs.invoiceassistant.domain;

/** Supplier evidence authored by the optional vision Query. */
public record VisionSupplierObservation(String supplier, SupplierEvidenceStatus supplierEvidenceStatus) {
    public VisionSupplierObservation {
        if (supplier == null || supplier.isBlank()) {
            supplier = "Unknown supplier";
        }
        if (supplierEvidenceStatus == null) {
            supplierEvidenceStatus = SupplierEvidenceStatus.INSUFFICIENT;
        }
        supplier = supplier.trim();
    }
}
