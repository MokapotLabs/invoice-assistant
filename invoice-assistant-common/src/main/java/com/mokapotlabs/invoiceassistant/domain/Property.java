package com.mokapotlabs.invoiceassistant.domain;

import java.util.List;

public record Property(
        String id, String displayName, String canonicalAddress, List<String> aliases, String streetNumberPrefix) {
    public Property {
        id = required(id, "id");
        displayName = required(displayName, "displayName");
        canonicalAddress = required(canonicalAddress, "canonicalAddress");
        aliases = List.copyOf(aliases);
        streetNumberPrefix = streetNumberPrefix == null ? "" : streetNumberPrefix.trim();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
