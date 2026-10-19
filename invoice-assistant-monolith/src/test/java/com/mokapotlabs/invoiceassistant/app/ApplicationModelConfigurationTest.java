package com.mokapotlabs.invoiceassistant.app;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class ApplicationModelConfigurationTest {
    @Test
    void absentAndBlankKeysKeepEveryModelLocal() throws Exception {
        for (Optional<String> key : List.of(Optional.<String>empty(), Optional.of(""), Optional.of("  \t"))) {
            var pipeline = pipeline();
            ApplicationDataInitializer.configureAiModels(pipeline, key);
            assertBinding(
                    pipeline,
                    "invoice-analysis-model",
                    "llm.query.openai.compatible",
                    "qwen3.5:4b",
                    "http://localhost:11434/v1",
                    "ollama");
            assertBinding(
                    pipeline,
                    "invoice-vision-model",
                    "llm.query.openai.compatible",
                    "qwen3-vl:4b-instruct",
                    "http://localhost:11434/v1",
                    "ollama");
            assertBinding(
                    pipeline,
                    "invoice-judgment-model",
                    "decision.query.jev",
                    "tev1",
                    "http://localhost:11434",
                    "ollama-decision");
        }
    }

    @Test
    void keySwitchesAllThreeBindingsWithoutPersistingItOrChangingTheFlow() throws Exception {
        var pipeline = pipeline();
        var original = pipeline();
        ApplicationDataInitializer.configureAiModels(pipeline, Optional.of(" fixture-secret-never-persist "));
        assertBinding(
                pipeline,
                "invoice-analysis-model",
                "llm.query.openai.compatible",
                "google/gemini-3.1-flash-lite",
                "https://openrouter.ai/api/v1",
                "openrouter-primary");
        assertBinding(
                pipeline,
                "invoice-vision-model",
                "llm.query.openai.compatible",
                "google/gemini-3.1-flash-lite",
                "https://openrouter.ai/api/v1",
                "openrouter-primary");
        assertBinding(
                pipeline,
                "invoice-judgment-model",
                "decision.query.jev",
                "typesafe/jev-1.13",
                "https://openrouter.ai/api/v1",
                "openrouter-primary");
        assertFalse(new Yaml().dump(pipeline).contains("fixture-secret-never-persist"));
        assertEquals(original.get("steps"), pipeline.get("steps"));
        assertEquals(original.get("publish"), pipeline.get("publish"));
        // Returning to local mode starts from the packaged defaults, never the previous runtime YAML.
        var restarted = pipeline();
        ApplicationDataInitializer.configureAiModels(restarted, Optional.empty());
        assertEquals(original, restarted);
    }

    @Test
    void refusesAnOverlayWhichChangesCompiledProviderIdentity() throws Exception {
        var pipeline = pipeline();
        binding(pipeline, "invoice-analysis-model").put("provider", "llm.query");
        assertThrows(
                java.io.IOException.class,
                () -> ApplicationDataInitializer.configureAiModels(pipeline, Optional.of("fixture-key")));
    }

    private static Map<String, Object> pipeline() throws Exception {
        try (var input = ApplicationDataInitializer.class.getResourceAsStream("/invoice-config/pipeline.yaml")) {
            assertNotNull(input);
            return new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> binding(Map<String, Object> pipeline, String name) {
        return (Map<String, Object>) ((Map<String, Object>) pipeline.get("connectors")).get(name);
    }

    @SuppressWarnings("unchecked")
    private static void assertBinding(
            Map<String, Object> pipeline,
            String name,
            String provider,
            String model,
            String baseUrl,
            String connection) {
        var binding = binding(pipeline, name);
        assertEquals(provider, binding.get("provider"));
        assertEquals(1, binding.get("version"));
        assertEquals(Map.of("model", model, "baseUrl", baseUrl, "connection", connection), binding.get("config"));
    }
}
