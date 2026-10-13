package com.mokapotlabs.invoiceassistant.interaction;

/** Authoritative application payload admitted by the property-confirmation Await boundary. */
public record SubmittedConfirmation(
        String propertyId, String supplier, String note, String emailRecipient, String emailSubject, String emailBody) {
    public SubmittedConfirmation(String propertyId, String supplier, String note) {
        this(propertyId, supplier, note, "accountant@example.invalid", "Invoice", "Invoice attached.");
    }

    public SubmittedConfirmation {
        if (propertyId == null || propertyId.isBlank())
            throw new IllegalArgumentException("propertyId must not be blank");
        propertyId = propertyId.trim();
        if (supplier == null || supplier.isBlank()) throw new IllegalArgumentException("supplier must not be blank");
        supplier = supplier.trim();
        note = note == null ? "" : note.trim();
        emailRecipient = required(emailRecipient, "emailRecipient");
        if (!emailRecipient.contains("@") || emailRecipient.startsWith("@") || emailRecipient.endsWith("@")) {
            throw new IllegalArgumentException("emailRecipient must be a valid email address");
        }
        emailSubject = required(emailSubject, "emailSubject");
        emailBody = required(emailBody, "emailBody");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
