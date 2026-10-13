package com.mokapotlabs.invoiceassistant.analysis;

import com.mokapotlabs.invoiceassistant.domain.Property;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Pure parser for the non-secret property matching projection of a catalogue file. */
public final class PropertyCatalogueParser {

    public com.mokapotlabs.invoiceassistant.domain.AutoApprovalPolicy approvalPolicy(Path catalogue) {
        Object value = root(catalogue).get("autoApprovalPolicy");
        if (!(value instanceof Map<?, ?> policy)) {
            return com.mokapotlabs.invoiceassistant.domain.AutoApprovalPolicy.manual();
        }
        Object configured = policy.get("deliveries");
        List<com.mokapotlabs.invoiceassistant.domain.AutomaticDelivery> deliveries = new ArrayList<>();
        if (configured instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> delivery) {
                    deliveries.add(new com.mokapotlabs.invoiceassistant.domain.AutomaticDelivery(
                            required(delivery, "propertyId"),
                            java.util.Objects.toString(delivery.get("recipient"), ""),
                            Boolean.TRUE.equals(delivery.get("ready"))));
                }
            }
        }
        return new com.mokapotlabs.invoiceassistant.domain.AutoApprovalPolicy(
                Boolean.TRUE.equals(policy.get("enabled")),
                java.time.Instant.parse(required(policy, "admittedAt")),
                deliveries,
                java.util.Objects.toString(policy.get("subjectTemplate"), ""),
                java.util.Objects.toString(policy.get("bodyTemplate"), ""));
    }

    public String filenameSeparator(Path catalogue) {
        Object value = root(catalogue).get("filenameSeparator");
        if (value == null || value.toString().isBlank()) {
            return "_";
        }
        return value.toString().trim();
    }

    public List<Property> parse(Path catalogue) {
        Map<?, ?> root = root(catalogue);
        {
            Object configured = root.get("properties");
            if (!(configured instanceof List<?> entries)) {
                throw new IllegalArgumentException("Property catalogue must contain properties");
            }
            List<Property> properties = new ArrayList<>();
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> property)) {
                    throw new IllegalArgumentException("Each catalogue property must be an object");
                }
                if (!enabled(property.get("enabled"))) {
                    continue;
                }
                properties.add(new Property(
                        required(property, "id"),
                        required(property, "displayName"),
                        required(property, "canonicalAddress"),
                        strings(property.get("aliases")),
                        required(property, "streetNumberPrefix")));
            }
            if (properties.isEmpty()) {
                throw new IllegalArgumentException("Configure at least one enabled property before analysing invoices");
            }
            return List.copyOf(properties);
        }
    }

    private static Map<?, ?> root(Path catalogue) {
        try (Reader reader = Files.newBufferedReader(catalogue, StandardCharsets.UTF_8)) {
            Object loaded = new Yaml(new SafeConstructor(loaderOptions())).load(reader);
            if (loaded instanceof Map<?, ?> root) return root;
            throw new IllegalArgumentException("Property catalogue root must be an object");
        } catch (IOException exception) {
            throw new IllegalArgumentException("Property catalogue could not be read", exception);
        }
    }

    private static LoaderOptions loaderOptions() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(10);
        options.setNestingDepthLimit(20);
        options.setCodePointLimit(2_000_000);
        return options;
    }

    private static boolean enabled(Object value) {
        return value == null || Boolean.parseBoolean(String.valueOf(value));
    }

    private static String required(Map<?, ?> value, String field) {
        Object candidate = value.get(field);
        if (candidate == null || candidate.toString().isBlank()) {
            throw new IllegalArgumentException("Catalogue property requires " + field);
        }
        return candidate.toString().trim();
    }

    private static List<String> strings(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> entries)) {
            throw new IllegalArgumentException("Property aliases must be a list");
        }
        return entries.stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(alias -> !alias.isBlank())
                .toList();
    }
}
