package com.mokapotlabs.invoiceassistant.settings;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Local email-only configuration. No credentials are stored here. */
public record ApplicationSettings(
        int schemaVersion,
        String filenameSeparator,
        List<ConfiguredProperty> properties,
        AccountantSettings accountant,
        boolean autoApprove) {
    public ApplicationSettings {
        if (schemaVersion < 1) throw new IllegalArgumentException("Invalid settings version");
        filenameSeparator = required(filenameSeparator, "filename separator");
        if (filenameSeparator.length() > 12 || filenameSeparator.contains("/") || filenameSeparator.contains("\\"))
            throw new IllegalArgumentException("Invalid filename separator");
        properties = List.copyOf(properties);
        Objects.requireNonNull(accountant);
        if (properties.stream().map(ConfiguredProperty::id).distinct().count() != properties.size())
            throw new IllegalArgumentException("Duplicate property identifiers");
    }

    public boolean isReady() {
        return properties.stream().anyMatch(ConfiguredProperty::deliveryReady) && accountant.isReady();
    }

    public Optional<ConfiguredProperty> property(String id) {
        return properties.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public String accountantRecipient(String id) {
        return property(id)
                .map(ConfiguredProperty::accountantRecipientOverride)
                .filter(s -> !s.isBlank())
                .orElse(accountant.recipient());
    }

    public record ConfiguredProperty(
            String id,
            String displayName,
            String canonicalAddress,
            List<String> aliases,
            String streetNumberPrefix,
            boolean enabled,
            String accountantRecipientOverride) {
        public ConfiguredProperty {
            id = required(id, "property id");
            displayName = required(displayName, "property name");
            canonicalAddress = required(canonicalAddress, "address");
            aliases = List.copyOf(aliases);
            streetNumberPrefix = required(streetNumberPrefix, "street number");
            accountantRecipientOverride =
                    Objects.requireNonNull(accountantRecipientOverride).trim();
            if (!accountantRecipientOverride.isBlank() && !validRecipient(accountantRecipientOverride))
                throw new IllegalArgumentException("Invalid recipient override");
        }

        public boolean deliveryReady() {
            return enabled;
        }
    }

    public record AccountantSettings(String recipient, String subjectTemplate, String bodyTemplate) {
        public AccountantSettings {
            recipient = Objects.requireNonNull(recipient, "recipient").trim();
            subjectTemplate = template(subjectTemplate, "subject template");
            bodyTemplate = template(bodyTemplate, "body template");
        }

        public boolean isReady() {
            return validRecipient(recipient);
        }

        private static String template(String value, String label) {
            String template = required(value, label);
            if (!template.replace("{property}", "")
                    .replace("{property-street-number}", "")
                    .replace("{filename}", "")
                    .replace("{supplier}", "")
                    .replace("{note}", "")
                    .matches("[^{}]*")) {
                throw new IllegalArgumentException(
                        label
                                + " may use only {property}, {property-street-number}, {filename}, {supplier} and {note} as placeholders");
            }
            return template;
        }
    }

    private static boolean validRecipient(String s) {
        return s.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    }

    private static String required(String s, String label) {
        Objects.requireNonNull(s, label);
        if (s.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return s.trim();
    }
}
