package com.mokapotlabs.invoiceassistant.domain;

import java.time.Instant;
import java.util.UUID;

public record ConfirmedInvoice(
        UUID documentId,
        UUID reviewId,
        String confirmedPropertyId,
        String propertyStreetNumber,
        boolean corrected,
        boolean supplierCorrected,
        boolean noteCorrected,
        String canonicalFilename,
        Instant confirmedAt,
        String supplier,
        String note,
        String emailRecipient,
        String emailSubject,
        String emailBody) {
    public ConfirmedInvoice(
            UUID documentId,
            UUID reviewId,
            String confirmedPropertyId,
            String propertyStreetNumber,
            boolean corrected,
            boolean supplierCorrected,
            boolean noteCorrected,
            String canonicalFilename,
            Instant confirmedAt,
            String supplier,
            String note) {
        this(
                documentId,
                reviewId,
                confirmedPropertyId,
                propertyStreetNumber,
                corrected,
                supplierCorrected,
                noteCorrected,
                canonicalFilename,
                confirmedAt,
                supplier,
                note,
                "accountant@example.invalid",
                canonicalFilename,
                note.isBlank() ? "Invoice attached." : note);
    }

    public ConfirmedInvoice(
            UUID documentId,
            String confirmedPropertyId,
            boolean corrected,
            String canonicalFilename,
            Instant confirmedAt,
            String supplier) {
        this(
                documentId,
                documentId,
                confirmedPropertyId,
                confirmedPropertyId,
                corrected,
                false,
                false,
                canonicalFilename,
                confirmedAt,
                supplier,
                "Invoice confirmed before mnemonic notes were available.",
                "accountant@example.invalid",
                canonicalFilename,
                "Invoice attached.");
    }

    public ConfirmedInvoice(
            UUID documentId,
            String confirmedPropertyId,
            boolean corrected,
            boolean supplierCorrected,
            boolean noteCorrected,
            String canonicalFilename,
            Instant confirmedAt,
            String supplier,
            String note) {
        this(
                documentId,
                documentId,
                confirmedPropertyId,
                confirmedPropertyId,
                corrected,
                supplierCorrected,
                noteCorrected,
                canonicalFilename,
                confirmedAt,
                supplier,
                note,
                "accountant@example.invalid",
                canonicalFilename,
                note.isBlank() ? "Invoice attached." : note);
    }

    public ConfirmedInvoice(
            UUID documentId,
            String confirmedPropertyId,
            String propertyStreetNumber,
            boolean corrected,
            boolean supplierCorrected,
            boolean noteCorrected,
            String canonicalFilename,
            Instant confirmedAt,
            String supplier,
            String note) {
        this(
                documentId,
                documentId,
                confirmedPropertyId,
                propertyStreetNumber,
                corrected,
                supplierCorrected,
                noteCorrected,
                canonicalFilename,
                confirmedAt,
                supplier,
                note,
                "accountant@example.invalid",
                canonicalFilename,
                note.isBlank() ? "Invoice attached." : note);
    }

    public ConfirmedInvoice {
        if (documentId == null || reviewId == null || confirmedAt == null) {
            throw new IllegalArgumentException("documentId, reviewId and confirmedAt must not be null");
        }
        if (confirmedPropertyId == null
                || confirmedPropertyId.isBlank()
                || canonicalFilename == null
                || canonicalFilename.isBlank()) {
            throw new IllegalArgumentException("confirmation fields must not be blank");
        }
        confirmedPropertyId = confirmedPropertyId.trim();
        if (propertyStreetNumber == null || propertyStreetNumber.isBlank()) {
            propertyStreetNumber = confirmedPropertyId;
        }
        propertyStreetNumber = propertyStreetNumber.trim();
        canonicalFilename = canonicalFilename.trim();
        if (supplier == null || supplier.isBlank()) {
            throw new IllegalArgumentException("confirmed supplier must not be blank");
        }
        if (note == null) note = "Invoice confirmed before mnemonic notes were available.";
        supplier = supplier.trim();
        note = note.trim();
        emailRecipient = required(emailRecipient, "email recipient");
        if (!emailRecipient.contains("@") || emailRecipient.startsWith("@") || emailRecipient.endsWith("@")) {
            throw new IllegalArgumentException("confirmed email recipient must be a valid email address");
        }
        emailSubject = required(emailSubject, "email subject");
        emailBody = required(emailBody, "email body");
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("confirmed " + label + " must not be blank");
        }
        return value.trim();
    }
}
