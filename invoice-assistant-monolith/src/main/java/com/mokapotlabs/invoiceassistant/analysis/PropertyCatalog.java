package com.mokapotlabs.invoiceassistant.analysis;

import com.mokapotlabs.invoiceassistant.domain.Property;
import com.mokapotlabs.invoiceassistant.settings.ApplicationSettings.ConfiguredProperty;
import com.mokapotlabs.invoiceassistant.settings.SettingsRepository;
import com.mokapotlabs.invoiceassistant.settings.SettingsState;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

/** Reads the local product catalogue on demand so settings changes need no restart. */
@ApplicationScoped
public class PropertyCatalog {
    private final SettingsRepository settingsRepository;

    public PropertyCatalog(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public List<Property> properties() {
        return settings().properties().stream()
                .filter(ConfiguredProperty::enabled)
                .map(PropertyCatalog::property)
                .toList();
    }

    public Optional<Property> find(String propertyId) {
        return properties().stream()
                .filter(property -> property.id().equals(propertyId))
                .findFirst();
    }

    private static Property property(ConfiguredProperty property) {
        return new Property(
                property.id(),
                property.displayName(),
                property.canonicalAddress(),
                property.aliases(),
                property.streetNumberPrefix());
    }

    private com.mokapotlabs.invoiceassistant.settings.ApplicationSettings settings() {
        SettingsState state = settingsRepository.load();
        if (state instanceof SettingsState.RepairRequired repair) {
            throw new IllegalStateException(repair.message());
        }
        return state.settings();
    }
}
