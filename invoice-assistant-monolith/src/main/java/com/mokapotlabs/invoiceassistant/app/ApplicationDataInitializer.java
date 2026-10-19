package com.mokapotlabs.invoiceassistant.app;

import com.mokapotlabs.invoiceassistant.settings.SettingsRepository;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ApplicationDataInitializer {
    private final InvoiceAssistantConfiguration configuration;
    private final SettingsRepository settingsRepository;
    private final boolean resetTestDataRoot;

    public ApplicationDataInitializer(
            InvoiceAssistantConfiguration configuration,
            SettingsRepository settingsRepository,
            @ConfigProperty(name = "invoice-assistant.test-reset-data-root", defaultValue = "false")
                    boolean resetTestDataRoot) {
        this.configuration = configuration;
        this.settingsRepository = settingsRepository;
        this.resetTestDataRoot = resetTestDataRoot;
    }

    void initialize(@Observes @Priority(1) StartupEvent ignored) throws IOException {
        if (resetTestDataRoot && LaunchMode.current() == LaunchMode.TEST) {
            deleteTestRoot(configuration.dataRoot());
        }
        Files.createDirectories(configuration.dataRoot().resolve("tmp/uploads"));
        Files.createDirectories(configuration.dataRoot().resolve("tmp/pdfbox"));
        Files.createDirectories(configuration.dataRoot().resolve("logs"));
        Files.createDirectories(configuration.dataRoot().resolve("runtime"));
        Files.createDirectories(configuration.dataRoot().resolve("database"));
        initializePayloadHost();
        settingsRepository.initializeAndLoad();
    }

    private void initializePayloadHost() throws IOException {
        Path keyFile = configuration.dataRoot().resolve("runtime/object-reference.key");
        if (System.getProperty("tpf.object.reference.hmac-key", "").isBlank()
                && System.getenv()
                        .getOrDefault("TPF_OBJECT_REFERENCE_HMAC_KEY", "")
                        .isBlank()) {
            if (Files.notExists(keyFile)) {
                byte[] key = new byte[32];
                new java.security.SecureRandom().nextBytes(key);
                Files.createFile(
                        keyFile,
                        java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                                java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
                Files.writeString(keyFile, java.util.Base64.getEncoder().encodeToString(key));
            }
            System.setProperty(
                    "tpf.object.reference.hmac-key", Files.readString(keyFile).trim());
        }
        try (var input = ApplicationDataInitializer.class.getResourceAsStream("/invoice-config/pipeline.yaml")) {
            if (input == null) throw new IOException("Packaged invoice pipeline is unavailable.");
            var yaml = new org.yaml.snakeyaml.Yaml(
                    new org.yaml.snakeyaml.constructor.SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()));
            java.util.Map<String, Object> root = yaml.load(input);
            configureAiModels(root, Optional.ofNullable(System.getenv("OPENROUTER_API_KEY")));
            for (String boundary : java.util.List.of("publish", "sources")) {
                @SuppressWarnings("unchecked")
                var objects = (java.util.Map<String, Object>) root.get(boundary);
                @SuppressWarnings("unchecked")
                var target = (java.util.Map<String, Object>) objects.get("invoice-document-store");
                target.put(
                        "location",
                        java.util.Map.of("root", configuration.dataRoot().toString()));
            }
            Path runtimeConfig = configuration.dataRoot().resolve("runtime/pipeline.yaml");
            Files.writeString(runtimeConfig, new org.yaml.snakeyaml.Yaml().dump(root));
            System.setProperty("pipeline.config", runtimeConfig.toString());
        }
    }

    /** Select all model bindings together; credentials never enter the persisted runtime YAML. */
    static void configureAiModels(Map<String, Object> root, Optional<String> apiKey) throws IOException {
        if (apiKey.filter(value -> !value.isBlank()).isEmpty()) return;
        try (var input = ApplicationDataInitializer.class.getResourceAsStream("/invoice-config/openrouter.yaml")) {
            if (input == null) throw new IOException("Packaged OpenRouter configuration is unavailable.");
            var yaml = new org.yaml.snakeyaml.Yaml(
                    new org.yaml.snakeyaml.constructor.SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()));
            Map<String, Object> overlay = yaml.load(input);
            @SuppressWarnings("unchecked")
            var bindings = (Map<String, Object>) root.get("connectors");
            @SuppressWarnings("unchecked")
            var replacements = (Map<String, Map<String, Object>>) overlay.get("connectors");
            for (var entry : replacements.entrySet()) {
                @SuppressWarnings("unchecked")
                var compiled = (Map<String, Object>) bindings.get(entry.getKey());
                if (!compiled.get("provider").equals(entry.getValue().get("provider"))
                        || !compiled.get("version").equals(entry.getValue().get("version"))) {
                    throw new IOException(
                            "Model provider identity differs from the compiled pipeline: " + entry.getKey());
                }
            }
            bindings.putAll(replacements);
        }
    }

    private static void deleteTestRoot(Path root) throws IOException {
        if (Files.notExists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException("Could not reset the test data root.", exception);
                }
            });
        }
    }
}
