package com.mokapotlabs.invoiceassistant.domain;

/** Truth for one obligation in the current workflow execution. */
public enum DeliveryObligationTruth {
    NOT_ATTEMPTED,
    ALREADY_SUCCEEDED,
    SUCCEEDED,
    FAILED,
    USER_ACTION_REQUIRED,
    EXTERNAL_SESSION_REQUIRED,
    SUBMISSION_UNCERTAIN,
    INTERRUPTED
}
