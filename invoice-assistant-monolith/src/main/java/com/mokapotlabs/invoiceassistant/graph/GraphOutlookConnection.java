package com.mokapotlabs.invoiceassistant.graph;

import com.microsoft.aad.msal4j.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** OAuth connection lifecycle belongs to the local host, separate from business execution. */
@ApplicationScoped
public class GraphOutlookConnection implements OutlookTokenProvider {
    public static final String AUTHORITY = "https://login.microsoftonline.com/consumers/";
    public static final Set<String> SCOPES = Set.of("https://graph.microsoft.com/Mail.Send");

    public enum Status {
        UNCONFIGURED,
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECT_REQUIRED,
        STORAGE_UNAVAILABLE
    }

    public record Challenge(String userCode, String verificationUri, Instant expiresAt) {
        public Challenge {
            java.util.Objects.requireNonNull(userCode);
            java.util.Objects.requireNonNull(expiresAt);
            var uri = java.net.URI.create(verificationUri);
            if (!"https".equals(uri.getScheme())
                    || !java.util.Set.of("microsoft.com", "www.microsoft.com", "login.microsoftonline.com")
                            .contains(uri.getHost()))
                throw new IllegalArgumentException("Microsoft sign-in URL is invalid.");
        }
    }

    public record State(Status status, String message, Optional<Challenge> challenge, Optional<String> account) {
        public State {
            java.util.Objects.requireNonNull(status);
            java.util.Objects.requireNonNull(message);
            java.util.Objects.requireNonNull(challenge);
            java.util.Objects.requireNonNull(account);
        }
    }

    private final GraphConfiguration configuration;
    private final java.util.function.Function<String, SecureTokenCache> cacheFactory;
    private final AtomicReference<State> state;
    private Optional<PublicClientApplication> application = Optional.empty();
    private Optional<SecureTokenCache> cache = Optional.empty();
    private Optional<CompletableFuture<IAuthenticationResult>> pending = Optional.empty();
    private long generation;

    @Inject
    public GraphOutlookConnection(GraphConfiguration configuration) {
        this(configuration, MacKeychainTokenCache::new);
    }

    GraphOutlookConnection(
            GraphConfiguration configuration, java.util.function.Function<String, SecureTokenCache> cacheFactory) {
        this.configuration = java.util.Objects.requireNonNull(configuration);
        this.cacheFactory = java.util.Objects.requireNonNull(cacheFactory);
        state = new AtomicReference<>(new State(
                configuration.clientId().isEmpty() ? Status.UNCONFIGURED : Status.DISCONNECTED,
                configuration.clientId().isEmpty()
                        ? "Set INVOICE_ASSISTANT_GRAPH_CLIENT_ID to your public-client app registration ID."
                        : "Connect Outlook to enable email submission.",
                Optional.empty(),
                Optional.empty()));
    }

    public State state() {
        return state.get();
    }

    private synchronized PublicClientApplication client() throws Exception {
        if (application.isPresent()) return application.orElseThrow();
        String id = configuration
                .clientId()
                .orElseThrow(() -> new IllegalStateException("Outlook app registration is not configured."));
        SecureTokenCache storage = cacheFactory.apply(id);
        var aspect = new ITokenCacheAccessAspect() {
            @Override
            public void beforeCacheAccess(ITokenCacheAccessContext context) {
                storage.read().ifPresent(context.tokenCache()::deserialize);
            }

            @Override
            public void afterCacheAccess(ITokenCacheAccessContext context) {
                if (context.hasCacheChanged())
                    storage.write(context.tokenCache().serialize());
            }
        };
        var client = PublicClientApplication.builder(id)
                .authority(AUTHORITY)
                .setTokenCacheAccessAspect(aspect)
                .connectTimeoutForDefaultHttpClient(30000)
                .readTimeoutForDefaultHttpClient(30000)
                .logPii(false)
                .build();
        cache = Optional.of(storage);
        application = Optional.of(client);
        return client;
    }

    public synchronized State connect() {
        if (configuration.clientId().isEmpty() || state.get().status() == Status.CONNECTING) return state.get();
        try {
            PublicClientApplication client = client();
            long requestGeneration = ++generation;
            state.set(new State(
                    Status.CONNECTING,
                    "Waiting for Microsoft sign-in instructions.",
                    Optional.empty(),
                    Optional.empty()));
            var parameters = DeviceCodeFlowParameters.builder(SCOPES, code -> {
                        synchronized (GraphOutlookConnection.this) {
                            if (generation == requestGeneration)
                                state.set(new State(
                                        Status.CONNECTING,
                                        "Open Microsoft's sign-in page and enter this code.",
                                        Optional.of(new Challenge(
                                                code.userCode(),
                                                code.verificationUri(),
                                                Instant.now().plusSeconds(code.expiresIn()))),
                                        Optional.empty()));
                        }
                    })
                    .build();
            var future = client.acquireToken(parameters);
            pending = Optional.of(future);
            future.whenComplete((result, error) -> {
                synchronized (GraphOutlookConnection.this) {
                    if (generation != requestGeneration) return;
                    pending = Optional.empty();
                    if (error != null) requireReconnect();
                    else
                        state.set(new State(
                                Status.CONNECTED,
                                "Outlook connected with delegated Mail.Send.",
                                Optional.empty(),
                                Optional.of(result.account().username())));
                }
            });
        } catch (Exception failure) {
            state.set(new State(
                    Status.STORAGE_UNAVAILABLE,
                    "Secure Outlook connection storage is unavailable. Unlock macOS Keychain and check the app registration.",
                    Optional.empty(),
                    Optional.empty()));
        }
        return state.get();
    }

    @Override
    public synchronized Optional<String> accessToken() {
        if (configuration.clientId().isEmpty()
                || state.get().status() == Status.CONNECTING
                || state.get().status() == Status.RECONNECT_REQUIRED
                || state.get().status() == Status.STORAGE_UNAVAILABLE) return Optional.empty();
        try {
            var client = client();
            var accounts = client.getAccounts().join();
            Optional<IAccount> chosen = state.get().account().flatMap(name -> accounts.stream()
                    .filter(account -> account.username().equals(name))
                    .findFirst());
            if (chosen.isEmpty() && accounts.size() == 1)
                chosen = Optional.of(accounts.iterator().next());
            if (chosen.isEmpty()) {
                requireReconnect();
                return Optional.empty();
            }
            var result = client.acquireTokenSilently(SilentParameters.builder(SCOPES, chosen.orElseThrow())
                            .build())
                    .join();
            state.set(new State(
                    Status.CONNECTED,
                    "Outlook connected with delegated Mail.Send.",
                    Optional.empty(),
                    Optional.of(result.account().username())));
            return Optional.of(result.accessToken());
        } catch (Exception failure) {
            requireReconnect();
            return Optional.empty();
        }
    }

    @Override
    public synchronized void requireReconnect() {
        state.set(new State(
                Status.RECONNECT_REQUIRED,
                "Connect Outlook again to renew sign-in or consent.",
                Optional.empty(),
                Optional.empty()));
    }

    public synchronized State disconnect() {
        if (configuration.clientId().isEmpty()) return state.get();
        ++generation;
        pending.ifPresent(future -> future.cancel(true));
        pending = Optional.empty();
        try {
            application.ifPresent(client -> client.getAccounts().join().forEach(account -> client.removeAccount(account)
                    .join()));
            if (configuration.clientId().isPresent())
                cache.orElseGet(() ->
                                cacheFactory.apply(configuration.clientId().orElseThrow()))
                        .clear();
            application = Optional.empty();
            cache = Optional.empty();
            state.set(new State(
                    Status.DISCONNECTED,
                    "Outlook disconnected; protected cached credentials removed.",
                    Optional.empty(),
                    Optional.empty()));
        } catch (Exception failure) {
            state.set(new State(
                    Status.STORAGE_UNAVAILABLE,
                    "Could not remove the protected Outlook cache. Unlock Keychain and retry disconnect.",
                    Optional.empty(),
                    Optional.empty()));
        }
        return state.get();
    }
}
