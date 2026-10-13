package com.mokapotlabs.invoiceassistant.domain;

public record PropertyRecommendation(String propertyId, String explanation) {
    public PropertyRecommendation {
        if (propertyId == null || propertyId.isBlank()) {
            throw new IllegalArgumentException("property id must not be blank");
        }
        if (explanation == null || explanation.isBlank()) {
            throw new IllegalArgumentException("explanation must not be blank");
        }
        propertyId = propertyId.trim();
        explanation = explanation.trim();
    }
}
