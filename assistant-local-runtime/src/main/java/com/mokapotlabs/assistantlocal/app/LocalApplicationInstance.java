package com.mokapotlabs.assistantlocal.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Owns the one local application instance. The endpoint file is advisory only: it is accepted
 * only when the recorded process is alive, another process owns the lock, and the local health
 * endpoint responds.
 */
@ApplicationScoped
public class LocalApplicationInstance {
    private static final Pattern LISTENING_PORT = Pattern.compile("(?:127\\.0\\.0\\.1|\\[::1\\]|localhost|\\*):(\\d+)");

    private final Path runtimeDirectory;
    private final Path lockPath;
    private final Path endpointPath;
    private final ObjectMapper objectMapper;
    private final boolean openBrowser;
    private final int grpcPort;
    private FileChannel lockChannel;
    private FileLock lock;
    private volatile boolean primary;

    public LocalApplicationInstance(
            @ConfigProperty(name = "invoice-assistant.data-root") Path dataRoot,
            @ConfigProperty(name = "assistant-local.instance-name") String instanceName,
            ObjectMapper objectMapper,
            @ConfigProperty(name = "assistant-local.ui.open-browser", defaultValue = "true") boolean openBrowser,
            @ConfigProperty(name = "quarkus.grpc.server.port", defaultValue = "9001") int grpcPort) {
        if (instanceName == null || !instanceName.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException(
                    "assistant-local.instance-name must use lowercase letters, digits or hyphens");
        }
        this.runtimeDirectory =
                dataRoot.toAbsolutePath().normalize().resolve("runtime").resolve(instanceName);
        this.lockPath = runtimeDirectory.resolve("instance.lock");
        this.endpointPath = runtimeDirectory.resolve("endpoint.json");
        this.objectMapper = objectMapper;
        this.openBrowser = openBrowser;
        this.grpcPort = grpcPort;
    }

    void claim(@Observes @Priority(10) StartupEvent ignored) throws IOException {
        Files.createDirectories(runtimeDirectory);
        lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            lock = lockChannel.tryLock();
        } catch (OverlappingFileLockException exception) {
            lock = null;
        }
        if (lock != null) {
            primary = true;
            deleteStaleEndpoint();
            startEndpointPublishing();
            return;
        }
        closeChannel();
        Optional<EndpointRecord> existing = readEndpoint();
        if (existing.filter(this::isLiveLockedEndpoint).isPresent()) {
            open(existing.orElseThrow().url());
            Quarkus.asyncExit(0);
            return;
        }
        throw new IllegalStateException(
                "Invoice Assistant is already starting or running, but its local UI is not reachable yet. Try again shortly.");
    }

    private void startEndpointPublishing() {
        Thread.startVirtualThread(() -> publishWhenPortIsReachable());
    }

    private void publishWhenPortIsReachable() {
        // Quarkus dev mode can spend several seconds compiling before binding the random HTTP port.
        for (int attempt = 0; attempt < 300; attempt++) {
            for (int port : applicationHttpPorts(resolveListeningPorts(), grpcPort)) {
                if (healthCheck("http://127.0.0.1:" + port + "/")) {
                    publish(port);
                    return;
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void publish(int port) {
        if (port < 1) {
            return;
        }
        EndpointRecord endpoint = new EndpointRecord(
                "http://127.0.0.1:" + port + "/", ProcessHandle.current().pid(), Instant.now());
        try {
            objectMapper.writeValue(endpointPath.toFile(), endpoint);
        } catch (IOException exception) {
            return;
        }
        if (openBrowser) open(endpoint.url());
    }

    private Set<Integer> resolveListeningPorts() {
        try {
            Process process = new ProcessBuilder(
                            "/usr/sbin/lsof",
                            "-nP",
                            "-a",
                            "-p",
                            Long.toString(ProcessHandle.current().pid()),
                            "-iTCP",
                            "-sTCP:LISTEN")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Set.of();
            }
            Matcher matcher = LISTENING_PORT.matcher(output);
            Set<Integer> ports = new LinkedHashSet<>();
            while (matcher.find()) {
                ports.add(Integer.parseInt(matcher.group(1)));
            }
            return ports;
        } catch (IOException exception) {
            return Set.of();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Set.of();
        }
    }

    /**
     * TPF exposes a gRPC listener in the same JVM. Its port is not an HTTP candidate: probing it
     * with {@code /health} produces an HTTP/2 protocol error and cannot discover the local UI.
     */
    static Set<Integer> applicationHttpPorts(Set<Integer> listeningPorts, int grpcPort) {
        Set<Integer> result = new LinkedHashSet<>();
        for (int port : listeningPorts) {
            if (port != grpcPort) {
                result.add(port);
            }
        }
        return result;
    }

    private boolean isLiveLockedEndpoint(EndpointRecord endpoint) {
        return ProcessHandle.of(endpoint.pid()).isPresent()
                && lockHeldByAnotherProcess()
                && healthCheck(endpoint.url());
    }

    private boolean lockHeldByAnotherProcess() {
        try (FileChannel probe = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            try (FileLock ignored = probe.tryLock()) {
                return false;
            } catch (OverlappingFileLockException exception) {
                return true;
            }
        } catch (IOException exception) {
            return false;
        }
    }

    static boolean healthCheck(String url) {
        try {
            HttpResponse<Void> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build()
                    .send(
                            HttpRequest.newBuilder(URI.create(url + "health"))
                                    .timeout(Duration.ofSeconds(2))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Optional<EndpointRecord> readEndpoint() {
        try {
            return Files.exists(endpointPath)
                    ? Optional.of(objectMapper.readValue(endpointPath.toFile(), EndpointRecord.class))
                    : Optional.empty();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private void deleteStaleEndpoint() throws IOException {
        Files.deleteIfExists(endpointPath);
    }

    private static void open(String url) {
        Path openCommand = Path.of("/usr/bin/open");
        if (Files.notExists(openCommand)) return;
        try {
            new ProcessBuilder(openCommand.toString(), url).start();
        } catch (IOException ignored) {
            // The local application remains reachable even if macOS cannot open a browser.
        }
    }

    @PreDestroy
    void close() {
        if (primary) {
            readEndpoint()
                    .filter(endpoint ->
                            endpoint.pid() == ProcessHandle.current().pid())
                    .ifPresent(ignored -> {
                        try {
                            Files.deleteIfExists(endpointPath);
                        } catch (IOException ignoredException) {
                            // Shutdown must not fail because a stale endpoint file remains.
                        }
                    });
        }
        try {
            if (lock != null && lock.isValid()) lock.release();
        } catch (IOException ignored) {
            // Shutdown must continue.
        }
        closeChannel();
    }

    private void closeChannel() {
        try {
            if (lockChannel != null) lockChannel.close();
        } catch (IOException ignored) {
            // Shutdown must continue.
        }
    }

    @RegisterForReflection
    record EndpointRecord(String url, long pid, Instant startedAt) {}
}
