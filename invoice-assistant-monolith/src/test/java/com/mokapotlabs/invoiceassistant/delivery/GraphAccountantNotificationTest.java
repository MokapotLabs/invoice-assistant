package com.mokapotlabs.invoiceassistant.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mokapotlabs.invoiceassistant.domain.*;
import com.mokapotlabs.invoiceassistant.graph.*;
import com.mokapotlabs.invoiceassistant.settings.SettingsRepository;
import com.mokapotlabs.invoiceassistant.storage.DeliveryOperationStatus;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.pipelineframework.repository.PayloadReference;

class GraphAccountantNotificationTest {
    @TempDir
    Path root;

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger status = new AtomicInteger(202);
    private final AtomicReference<String> body = new AtomicReference<>("");
    private final AtomicBoolean reconnect = new AtomicBoolean();
    private final ObjectMapper json = new ObjectMapper();
    private GraphAccountantNotification notification;
    private InvoiceAttachmentReader reader;
    private InvoiceDocument document;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1.0/me/sendMail", exchange -> {
            calls.incrementAndGet();
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
        var settings = new SettingsRepository(root, "/defaults/test-settings.yaml");
        settings.initializeAndLoad();
        var configuration = new GraphConfiguration(
                Optional.empty(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1.0/me/sendMail"),
                Duration.ofSeconds(3));
        var tokens = new OutlookTokenProvider() {
            public Optional<String> accessToken() {
                return Optional.of("fixture-token");
            }

            public void requireReconnect() {
                reconnect.set(true);
            }
        };
        reader = mock(InvoiceAttachmentReader.class);
        when(reader.read(any())).thenReturn("%PDF-fixture".getBytes(StandardCharsets.UTF_8));
        notification = new GraphAccountantNotification(
                configuration,
                tokens,
                reader,
                settings,
                json,
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
        UUID id = UUID.randomUUID();
        var reference = new PayloadReference(
                "filesystem",
                root.toString(),
                "fixture.pdf",
                "application/pdf",
                "raw",
                "sha256",
                12,
                "v1",
                Map.of(),
                Optional.empty());
        document = new InvoiceDocument(id, "original.pdf", "application/pdf", 12, "sha256", Instant.now(), reference);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ConfirmedInvoice confirmation(String recipient) {
        return new ConfirmedInvoice(
                document.documentId(),
                UUID.randomUUID(),
                "maple-street",
                "101",
                false,
                false,
                false,
                "101_invoice.pdf",
                Instant.now(),
                "Supplier",
                "Note",
                recipient,
                "Confirmed subject",
                "Confirmed body");
    }

    @Test
    void submitsOnlyConfirmedMessageAndReportsAcceptance() throws Exception {
        var result = notification.notifyAccountant(document, confirmation("accountant@example.test"));
        assertEquals(DeliveryOperationStatus.SUCCEEDED, result.status());
        assertTrue(result.message().contains("not verified"));
        assertEquals(1, calls.get());
        var request = json.readTree(body.get());
        assertTrue(request.path("saveToSentItems").asBoolean());
        assertEquals("Confirmed subject", request.at("/message/subject").asText());
        assertEquals("Confirmed body", request.at("/message/body/content").asText());
        assertEquals(
                "101_invoice.pdf", request.at("/message/attachments/0/name").asText());
        assertArrayEquals(
                "%PDF-fixture".getBytes(),
                Base64.getDecoder()
                        .decode(request.at("/message/attachments/0/contentBytes")
                                .asText()));
    }

    @Test
    void allowlistAndAttachmentLimitsRejectBeforeNetwork() {
        assertEquals(
                DeliveryOperationStatus.USER_ACTION_REQUIRED,
                notification
                        .notifyAccountant(document, confirmation("unconfigured@example.test"))
                        .status());
        var tooLarge = new InvoiceDocument(
                document.documentId(),
                "large.pdf",
                "application/pdf",
                2_900_001,
                "sha",
                Instant.now(),
                document.payloadReference());
        assertEquals(
                DeliveryOperationStatus.USER_ACTION_REQUIRED,
                notification
                        .notifyAccountant(tooLarge, confirmation("accountant@example.test"))
                        .status());
        assertEquals(0, calls.get());
        verifyNoInteractions(reader);
    }

    @Test
    void rejectedConsentRequiresReconnectAndServerFailureIsNotRetryable() {
        status.set(401);
        assertEquals(
                DeliveryOperationStatus.EXTERNAL_SESSION_REQUIRED,
                notification
                        .notifyAccountant(document, confirmation("accountant@example.test"))
                        .status());
        assertTrue(reconnect.get());
        status.set(503);
        var result = notification.notifyAccountant(document, confirmation("accountant@example.test"));
        assertEquals(DeliveryOperationStatus.SUBMISSION_UNCERTAIN, result.status());
        assertFalse(com.mokapotlabs.invoiceassistant.storage.DeliveryOperationState.notStarted()
                .record(result.status(), result.message(), result.externalReference())
                .retryable());
        assertEquals(2, calls.get());
    }
}
