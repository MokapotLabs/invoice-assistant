package com.mokapotlabs.assistantlocal.connection;

import jakarta.enterprise.context.ApplicationScoped;
import java.net.http.HttpClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.pipelineframework.connector.*;
import org.pipelineframework.connector.decision.jev.AuthenticatedJevConnection;

/** Ollama's decision protocol uses a non-secret bearer marker. */
@ApplicationScoped
public class LocalDecisionConnectionResolver implements ConnectionResolver {
    private static final HttpClient CLIENT =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    public <C extends ResolvedConnection> CompletionStage<C> resolve(ConnectionResolutionRequest<C> request) {
        if (!request.reference().equals(new ConnectionRef("ollama-decision"))
                || request.connectionType() != AuthenticatedJevConnection.class)
            return CompletableFuture.failedStage(
                    new ConnectionResolutionException("Unsupported local decision connection"));
        return CompletableFuture.completedStage(
                request.connectionType().cast(AuthenticatedJevConnection.bearer(CLIENT, "ollama")));
    }
}
