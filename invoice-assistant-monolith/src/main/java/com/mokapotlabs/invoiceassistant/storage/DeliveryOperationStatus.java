package com.mokapotlabs.invoiceassistant.storage;

public enum DeliveryOperationStatus {
    NOT_STARTED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    USER_ACTION_REQUIRED,
    EXTERNAL_SESSION_REQUIRED,
    SUBMISSION_UNCERTAIN,
    INTERRUPTED
}
