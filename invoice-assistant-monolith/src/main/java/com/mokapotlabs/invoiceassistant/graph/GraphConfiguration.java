package com.mokapotlabs.invoiceassistant.graph;

import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.Config;

@ApplicationScoped
public class GraphConfiguration {
    public static final long MAX_ATTACHMENT_BYTES = 2_900_000;
    public static final long MAX_REQUEST_BYTES = 3_900_000;
    private final Optional<String> clientId;
    private final URI endpoint;
    private final Duration timeout;

    @jakarta.inject.Inject
    public GraphConfiguration(Config config) {
        this(
                config.getOptionalValue("invoice-assistant.graph.client-id", String.class),
                URI.create("https://graph.microsoft.com/v1.0/me/sendMail"),
                Duration.ofSeconds(30));
    }

    public GraphConfiguration(Optional<String> clientId, URI endpoint, Duration timeout) {
        this.clientId = Objects.requireNonNull(clientId).map(String::trim).filter(value -> !value.isEmpty());
        this.clientId.ifPresent(UUID::fromString);
        this.endpoint = Objects.requireNonNull(endpoint);
        boolean loopback = java.util.Set.of("localhost", "127.0.0.1", "::1").contains(endpoint.getHost());
        if (!(endpoint.getScheme().equals("https") && endpoint.getHost().equals("graph.microsoft.com"))
                && !(endpoint.getScheme().equals("http") && loopback))
            throw new IllegalArgumentException("Graph endpoint must be Microsoft Graph or a loopback fixture.");
        if (endpoint.getUserInfo() != null || endpoint.getFragment() != null || endpoint.getQuery() != null)
            throw new IllegalArgumentException("Graph endpoint must not contain credentials, query or fragment.");
        this.timeout = Objects.requireNonNull(timeout);
        if (timeout.isZero() || timeout.isNegative())
            throw new IllegalArgumentException("Graph timeout must be positive.");
    }

    public Optional<String> clientId() {
        return clientId;
    }

    public URI endpoint() {
        return endpoint;
    }

    public Duration timeout() {
        return timeout;
    }
}
