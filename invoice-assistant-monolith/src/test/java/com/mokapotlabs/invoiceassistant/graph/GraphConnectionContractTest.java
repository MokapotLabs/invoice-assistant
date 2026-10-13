package com.mokapotlabs.invoiceassistant.graph;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphConnectionContractTest {
    @Test
    void unconfiguredHostDoesNotStartOAuthOrExposeTokens() {
        var connection = new GraphOutlookConnection(new GraphConfiguration(
                Optional.empty(), URI.create("https://graph.microsoft.com/v1.0/me/sendMail"), Duration.ofSeconds(30)));
        assertEquals(
                GraphOutlookConnection.Status.UNCONFIGURED, connection.connect().status());
        assertTrue(connection.accessToken().isEmpty());
        assertTrue(connection.state().challenge().isEmpty());
        assertEquals(java.util.Set.of("https://graph.microsoft.com/Mail.Send"), GraphOutlookConnection.SCOPES);
        assertEquals("https://login.microsoftonline.com/consumers/", GraphOutlookConnection.AUTHORITY);
    }

    @org.junit.jupiter.api.Test
    void disconnectRemovesPersistedCacheEvenBeforeClientInitialization() {
        var deleted = new java.util.concurrent.atomic.AtomicBoolean();
        var configuration = new GraphConfiguration(
                java.util.Optional.of("00000000-0000-0000-0000-000000000001"),
                java.net.URI.create("https://graph.microsoft.com/v1.0/me/sendMail"),
                java.time.Duration.ofSeconds(3));
        var connection = new GraphOutlookConnection(configuration, ignored -> new SecureTokenCache() {
            public java.util.Optional<String> read() {
                throw new AssertionError("Disconnect need not deserialize tokens");
            }

            public void write(String value) {
                throw new AssertionError("Disconnect must not write tokens");
            }

            public void clear() {
                deleted.set(true);
            }
        });
        assertEquals(
                GraphOutlookConnection.Status.DISCONNECTED,
                connection.disconnect().status());
        assertTrue(deleted.get());
    }

    @org.junit.jupiter.api.Test
    void failedCacheRemovalBlocksSubsequentSending() {
        var configuration = new GraphConfiguration(
                java.util.Optional.of("00000000-0000-0000-0000-000000000001"),
                java.net.URI.create("https://graph.microsoft.com/v1.0/me/sendMail"),
                java.time.Duration.ofSeconds(3));
        var connection = new GraphOutlookConnection(configuration, ignored -> new SecureTokenCache() {
            public java.util.Optional<String> read() {
                throw new AssertionError("Failed disconnect must block silent authentication");
            }

            public void write(String value) {
                throw new AssertionError();
            }

            public void clear() {
                throw new IllegalStateException("Fixture locked storage");
            }
        });
        assertEquals(
                GraphOutlookConnection.Status.STORAGE_UNAVAILABLE,
                connection.disconnect().status());
        assertTrue(connection.accessToken().isEmpty());
    }
}
