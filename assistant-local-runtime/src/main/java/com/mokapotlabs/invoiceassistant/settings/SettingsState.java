package com.mokapotlabs.invoiceassistant.settings;

/** A malformed file is repairable product state, never a startup failure. */
public sealed interface SettingsState permits SettingsState.Ready, SettingsState.RepairRequired {
    ApplicationSettings settings();

    default boolean repairRequired() {
        return this instanceof RepairRequired;
    }

    default String message() {
        return this instanceof RepairRequired repair ? repair.message() : "";
    }

    record Ready(ApplicationSettings settings) implements SettingsState {}

    record RepairRequired(ApplicationSettings settings, String message) implements SettingsState {}
}
