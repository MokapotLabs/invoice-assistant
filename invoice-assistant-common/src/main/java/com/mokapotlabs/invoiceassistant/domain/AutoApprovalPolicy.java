package com.mokapotlabs.invoiceassistant.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable delivery authority captured at invoice admission, excluded from model input. */
public record AutoApprovalPolicy(
        boolean enabled,
        Instant admittedAt,
        List<AutomaticDelivery> deliveries,
        String subjectTemplate,
        String bodyTemplate) {
    public AutoApprovalPolicy {
        Objects.requireNonNull(admittedAt);
        deliveries = List.copyOf(Objects.requireNonNull(deliveries));
        Objects.requireNonNull(subjectTemplate);
        Objects.requireNonNull(bodyTemplate);
    }

    public static AutoApprovalPolicy manual() {
        return new AutoApprovalPolicy(false, Instant.EPOCH, List.of(), "", "");
    }
}
