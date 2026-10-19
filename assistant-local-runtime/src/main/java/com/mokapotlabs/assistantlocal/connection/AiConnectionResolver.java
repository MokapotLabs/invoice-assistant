package com.mokapotlabs.assistantlocal.connection;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.http.HttpClient;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import org.pipelineframework.connector.*;
import org.pipelineframework.connector.decision.jev.AuthenticatedJevConnection;
import org.pipelineframework.connector.llm.langchain4j.AuthenticatedOpenAiCompatibleReactiveConnection;

/** Host-owned credentials for local Ollama and optional OpenRouter model bindings. */
@ApplicationScoped
public class AiConnectionResolver implements ConnectionResolver {
    private static final HttpClient CLIENT =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final Supplier<Optional<String>> apiKeySupplier;

    public AiConnectionResolver() {
        this(() -> Optional.ofNullable(System.getenv("OPENROUTER_API_KEY")));
    }

    public AiConnectionResolver(Supplier<Optional<String>> apiKeySupplier) {
        this.apiKeySupplier = Objects.requireNonNull(apiKeySupplier);
    }

    @Override
    public <C extends ResolvedConnection> CompletionStage<C> resolve(ConnectionResolutionRequest<C> request) {
        try {
            String reference = request.reference().value();
            boolean remote = reference.equals("openrouter-primary");
            if (!remote && !reference.equals("ollama") && !reference.equals("ollama-decision")) {
                throw new ConnectionResolutionException("Unsupported AI connection reference: " + reference);
            }
            String key = remote
                    ? apiKeySupplier
                            .get()
                            .map(String::trim)
                            .filter(value -> !value.isEmpty())
                            .orElseThrow(() -> new ConnectionResolutionException(
                                    ConnectionResolutionException.Kind.AUTHENTICATION_REQUIRED,
                                    "OpenRouter credentials are unavailable in the application host"))
                    : "ollama";
            if ((remote || reference.equals("ollama-decision"))
                    && request.connectionType() == AuthenticatedJevConnection.class) {
                return CompletableFuture.completedStage(
                        request.connectionType().cast(AuthenticatedJevConnection.bearer(CLIENT, key)));
            }
            if ((!remote && !reference.equals("ollama"))
                    || request.connectionType() != AuthenticatedOpenAiCompatibleReactiveConnection.class) {
                throw new ConnectionResolutionException("Unsupported AI connection type for " + reference);
            }
            var connection = new AuthenticatedOpenAiCompatibleReactiveConnection(configuration -> {
                var builder = OpenAiStreamingChatModel.builder()
                        .baseUrl(configuration.baseUrl())
                        .modelName(configuration.modelName())
                        .apiKey(key)
                        .timeout(configuration.timeout())
                        .strictJsonSchema(configuration.strictJsonSchema());
                if (!remote) builder.reasoningEffort("none");
                return builder.build();
            });
            return CompletableFuture.completedStage(request.connectionType().cast(connection));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedStage(failure);
        }
    }
}
