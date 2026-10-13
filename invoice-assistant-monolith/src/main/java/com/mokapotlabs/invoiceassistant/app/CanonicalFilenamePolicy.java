package com.mokapotlabs.invoiceassistant.app;

import com.mokapotlabs.invoiceassistant.domain.InvoiceDocument;
import com.mokapotlabs.invoiceassistant.domain.InvoiceReview;
import com.mokapotlabs.invoiceassistant.domain.Property;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.Locale;

public class CanonicalFilenamePolicy {
    private static final int MAX_FILENAME_LENGTH = 180;

    public String filename(InvoiceDocument document, Property property, InvoiceReview review) {
        return filename(document, property, review, review.facts().supplier());
    }

    public String filename(
            InvoiceDocument document, Property property, InvoiceReview review, String confirmedSupplier) {
        return filename(document, property, review, confirmedSupplier, "_");
    }

    public String filename(
            InvoiceDocument document,
            Property property,
            InvoiceReview review,
            String confirmedSupplier,
            String separator) {
        if (document == null) {
            throw new IllegalArgumentException("The invoice document is required.");
        }
        String streetNumber = property.streetNumberPrefix();
        if (streetNumber.isBlank()) {
            throw new IllegalArgumentException("The selected property has no street number.");
        }
        if (separator == null || separator.isBlank()) {
            throw new IllegalArgumentException("The filename separator is required.");
        }
        String supplier = safeComponent(confirmedSupplier, "supplier");
        String invoiceNumber = safeComponent(review.facts().invoiceNumber(), "invoice number");
        String amount = formatUsd(review.facts().totalAmount());
        String candidate = streetNumber + separator + supplier + separator + "invoice" + separator + invoiceNumber
                + separator + "(" + amount + ").pdf";
        if (candidate.length() > MAX_FILENAME_LENGTH) {
            throw new IllegalArgumentException("The canonical filename is too long.");
        }
        return candidate;
    }

    private static String safeComponent(String value, String label) {
        String safe = Normalizer.normalize(value, Normalizer.Form.NFC)
                .codePoints()
                .mapToObj(CanonicalFilenamePolicy::safeFilenameCharacter)
                .collect(java.util.stream.Collectors.joining())
                .replaceAll("\\s+", " ")
                .trim();
        if (safe.isBlank()) {
            throw new IllegalArgumentException("The " + label + " is unsafe.");
        }
        return safe;
    }

    private static String formatUsd(BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("The invoice total must not be negative.");
        }
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }

    private static String safeFilenameCharacter(int codePoint) {
        if (Character.isLetterOrDigit(codePoint)
                || codePoint == ' '
                || codePoint == '.'
                || codePoint == '_'
                || codePoint == '('
                || codePoint == ')'
                || codePoint == '-'
                || codePoint == '$'
                || codePoint == ',') {
            return new String(Character.toChars(codePoint));
        }
        return " ";
    }
}
