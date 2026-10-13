package com.mokapotlabs.invoiceassistant.storage;

final class UploadValidationException extends RuntimeException {
    UploadValidationException(String message) {
        super(message);
    }
}
