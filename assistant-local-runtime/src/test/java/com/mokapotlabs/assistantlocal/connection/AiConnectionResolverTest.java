package com.mokapotlabs.assistantlocal.connection;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.pipelineframework.connector.*;
import org.pipelineframework.connector.decision.jev.AuthenticatedJevConnection;
import org.pipelineframework.connector.llm.langchain4j.AuthenticatedOpenAiCompatibleReactiveConnection;

class AiConnectionResolverTest {
    @Test
    void localConnectionsNeverReadRemoteCredentials() {
        var resolver = new AiConnectionResolver(() -> {
            throw new AssertionError("Local AI must not read a remote key");
        });
        assertInstanceOf(
                AuthenticatedOpenAiCompatibleReactiveConnection.class,
                resolve(resolver, "ollama", AuthenticatedOpenAiCompatibleReactiveConnection.class));
        assertInstanceOf(
                AuthenticatedJevConnection.class,
                resolve(resolver, "ollama-decision", AuthenticatedJevConnection.class));
    }

    @Test
    void remoteCredentialsResolveBothTypedConnections() {
        var resolver = new AiConnectionResolver(() -> Optional.of(" fixture-key "));
        assertInstanceOf(
                AuthenticatedOpenAiCompatibleReactiveConnection.class,
                resolve(resolver, "openrouter-primary", AuthenticatedOpenAiCompatibleReactiveConnection.class));
        assertInstanceOf(
                AuthenticatedJevConnection.class,
                resolve(resolver, "openrouter-primary", AuthenticatedJevConnection.class));
    }

    @Test
    void missingOrBlankRemoteCredentialsFailWithoutFallingBack() {
        for (Optional<String> key : java.util.List.of(Optional.<String>empty(), Optional.of(""), Optional.of(" "))) {
            var resolver = new AiConnectionResolver(() -> key);
            for (var type : java.util.List.of(
                    AuthenticatedOpenAiCompatibleReactiveConnection.class, AuthenticatedJevConnection.class)) {
                var failure =
                        assertThrows(CompletionException.class, () -> resolve(resolver, "openrouter-primary", type));
                var cause = assertInstanceOf(ConnectionResolutionException.class, failure.getCause());
                assertEquals(ConnectionResolutionException.Kind.AUTHENTICATION_REQUIRED, cause.kind());
            }
        }
    }

    @Test
    void rejectsUnknownReferencesAndMismatchedTypes() {
        var resolver = new AiConnectionResolver(() -> Optional.of("fixture-key"));
        assertThrows(CompletionException.class, () -> resolve(resolver, "other", AuthenticatedJevConnection.class));
        assertThrows(CompletionException.class, () -> resolve(resolver, "ollama", AuthenticatedJevConnection.class));
        assertThrows(
                CompletionException.class,
                () -> resolve(resolver, "ollama-decision", AuthenticatedOpenAiCompatibleReactiveConnection.class));
    }

    private static <C extends ResolvedConnection> C resolve(
            AiConnectionResolver resolver, String reference, Class<C> type) {
        return resolver.resolve(new ConnectionResolutionRequest<>(
                        new ConnectionRef(reference), type, ConnectorExecutionContext.empty()))
                .toCompletableFuture()
                .join();
    }
}
