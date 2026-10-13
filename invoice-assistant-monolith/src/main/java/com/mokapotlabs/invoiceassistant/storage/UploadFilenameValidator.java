package com.mokapotlabs.invoiceassistant.storage;

import java.util.Locale;

final class UploadFilenameValidator {
    private UploadFilenameValidator() {}

    static String validate(String filename, String mediaType) {
        if (filename == null || filename.isBlank()) {
            throw new UploadValidationException("Choose a PDF to upload.");
        }
        String normalized = java.text.Normalizer.normalize(filename, java.text.Normalizer.Form.NFC)
                .trim();
        if (normalized.equals(".")
                || normalized.equals("..")
                || normalized.contains("/")
                || normalized.contains("\\")
                || normalized.codePoints().anyMatch(UploadFilenameValidator::isUnsafeCodePoint)) {
            throw new UploadValidationException("The file name contains unsupported characters.");
        }
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new UploadValidationException("Only PDF files can be uploaded.");
        }
        String normalizedMediaType = mediaType == null ? "" : mediaType.trim().toLowerCase(Locale.ROOT);
        if (!normalizedMediaType.isEmpty()
                && !"application/pdf".equals(normalizedMediaType)
                && !"application/octet-stream".equals(normalizedMediaType)) {
            throw new UploadValidationException("The uploaded media type is not a PDF.");
        }
        return normalized;
    }

    private static boolean isUnsafeCodePoint(int codePoint) {
        return Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT;
    }
}
