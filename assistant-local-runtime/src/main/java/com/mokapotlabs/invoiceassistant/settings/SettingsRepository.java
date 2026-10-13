package com.mokapotlabs.invoiceassistant.settings;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@ApplicationScoped
public class SettingsRepository {
    private final Path settingsPath;
    private final String seedResource;

    @Inject
    public SettingsRepository(
            @ConfigProperty(name = "invoice-assistant.data-root") Path dataRoot,
            @ConfigProperty(name = "invoice-assistant.settings.seed", defaultValue = "/defaults/settings.yaml")
                    String seedResource) {
        this.settingsPath = dataRoot.toAbsolutePath().normalize().resolve("config/settings.yaml");
        this.seedResource = seedResource;
    }

    public synchronized SettingsState initializeAndLoad() {
        try {
            if (Files.notExists(settingsPath)) {
                Files.createDirectories(settingsPath.getParent());
                try (InputStream input = SettingsRepository.class.getResourceAsStream(seedResource)) {
                    if (input == null) {
                        throw new IOException("packaged settings seed is missing");
                    }
                    atomicWrite(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            return new SettingsState.Ready(read());
        } catch (Exception exception) {
            return new SettingsState.RepairRequired(seed(), "Settings need repair: " + safeMessage(exception));
        }
    }

    public synchronized SettingsState load() {
        return initializeAndLoad();
    }

    public synchronized void save(ApplicationSettings settings) throws IOException {
        atomicWrite(render(settings));
    }

    public Path path() {
        return settingsPath;
    }

    private ApplicationSettings read() throws IOException {
        try (Reader reader = Files.newBufferedReader(settingsPath, StandardCharsets.UTF_8)) {
            Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
            if (!(parsed instanceof Map<?, ?> root)) {
                throw new IllegalArgumentException("settings must be a YAML object");
            }
            return from(root);
        }
    }

    private ApplicationSettings seed() {
        try (InputStream input = SettingsRepository.class.getResourceAsStream(seedResource)) {
            if (input == null) throw new IOException("packaged settings seed is missing");
            Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
            if (!(parsed instanceof Map<?, ?> root)) throw new IllegalArgumentException("settings seed is invalid");
            return from(root);
        } catch (IOException exception) {
            throw new IllegalStateException("packaged settings seed is invalid", exception);
        }
    }

    private void atomicWrite(String yaml) throws IOException {
        Files.createDirectories(settingsPath.getParent());
        Path temporary = Files.createTempFile(settingsPath.getParent(), ".settings-", ".tmp");
        Files.writeString(temporary, yaml, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, settingsPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, settingsPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private ApplicationSettings from(Map<?, ?> root) {
        Object accountantValue = root.get("accountant");
        if (!(accountantValue instanceof Map<?, ?> accountant)) {
            throw new IllegalArgumentException("settings must contain accountant");
        }
        List<ApplicationSettings.ConfiguredProperty> properties = new ArrayList<>();
        Object values = root.get("properties");
        if (!(values instanceof List<?> entries))
            throw new IllegalArgumentException("settings must contain properties");
        for (Object value : entries) {
            if (!(value instanceof Map<?, ?> property))
                throw new IllegalArgumentException("each property must be an object");
            properties.add(new ApplicationSettings.ConfiguredProperty(
                    required(property, "id"),
                    required(property, "displayName"),
                    required(property, "canonicalAddress"),
                    strings(property.get("aliases")),
                    required(property, "streetNumberPrefix"),
                    Boolean.parseBoolean(String.valueOf(orDefault(property, "enabled", Boolean.TRUE))),
                    optionalString(property, "accountantRecipientOverride")));
        }
        return new ApplicationSettings(
                Integer.parseInt(String.valueOf(orDefault(root, "schemaVersion", 1))),
                required(root, "filenameSeparator"),
                properties,
                new ApplicationSettings.AccountantSettings(
                        optionalString(accountant, "recipient"),
                        required(accountant, "subjectTemplate"),
                        required(accountant, "bodyTemplate")),
                Boolean.TRUE.equals(root.get("autoApprove")));
    }

    private static String render(ApplicationSettings settings) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", settings.schemaVersion());
        root.put("filenameSeparator", settings.filenameSeparator());
        root.put("autoApprove", settings.autoApprove());
        List<Map<String, Object>> properties = new ArrayList<>();
        for (ApplicationSettings.ConfiguredProperty property : settings.properties()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", property.id());
            value.put("displayName", property.displayName());
            value.put("canonicalAddress", property.canonicalAddress());
            value.put("aliases", property.aliases());
            value.put("streetNumberPrefix", property.streetNumberPrefix());
            value.put("enabled", property.enabled());
            value.put("accountantRecipientOverride", property.accountantRecipientOverride());
            properties.add(value);
        }
        root.put("properties", properties);
        root.put(
                "accountant",
                Map.of(
                        "recipient",
                        settings.accountant().recipient(),
                        "subjectTemplate",
                        settings.accountant().subjectTemplate(),
                        "bodyTemplate",
                        settings.accountant().bodyTemplate()));
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(root);
    }

    private static String required(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException(key + " must not be blank");
        return value.toString().trim();
    }

    private static String optionalString(Map<?, ?> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private static List<String> strings(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> values)) throw new IllegalArgumentException("aliases must be a list");
        return values.stream().map(String::valueOf).map(String::trim).toList();
    }

    private static Object orDefault(Map<?, ?> values, String key, Object defaultValue) {
        Object value = values.get(key);
        return value == null ? defaultValue : value;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "the configuration file is invalid" : message;
    }
}
