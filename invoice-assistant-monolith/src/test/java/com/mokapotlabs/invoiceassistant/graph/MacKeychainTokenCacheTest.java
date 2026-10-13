package com.mokapotlabs.invoiceassistant.graph;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;

@EnabledOnOs(OS.MAC)
@EnabledIfEnvironmentVariable(named = "RUN_KEYCHAIN_FIXTURES", matches = "true")
class MacKeychainTokenCacheTest {
    @Test
    void protectsAndRemovesAnIsolatedSyntheticCache() {
        var cache = new MacKeychainTokenCache("fixture-" + UUID.randomUUID());
        try {
            assertTrue(cache.read().isEmpty());
            cache.write("{\"fixture\":\"synthetic value — no real credentials\"}");
            assertTrue(cache.read().orElseThrow().contains("synthetic value"));
            cache.write("{\"fixture\":\"updated\"}");
            assertTrue(cache.read().orElseThrow().contains("updated"));
            cache.clear();
            assertTrue(cache.read().isEmpty());
        } finally {
            cache.clear();
        }
    }
}
