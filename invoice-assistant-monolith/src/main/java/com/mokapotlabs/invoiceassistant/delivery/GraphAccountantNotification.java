package com.mokapotlabs.invoiceassistant.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mokapotlabs.invoiceassistant.app.NotifyAccountantResult;
import com.mokapotlabs.invoiceassistant.domain.ConfirmedInvoice;
import com.mokapotlabs.invoiceassistant.domain.InvoiceDocument;
import com.mokapotlabs.invoiceassistant.graph.*;
import com.mokapotlabs.invoiceassistant.settings.SettingsRepository;
import com.mokapotlabs.invoiceassistant.storage.DeliveryOperationStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.http.*;
import java.util.*;

/** One Graph submission behind the TPF notification Command; never performs implicit sign-in or retries. */
@ApplicationScoped
public class GraphAccountantNotification {
    private final GraphConfiguration configuration;
    private final OutlookTokenProvider tokens;
    private final InvoiceAttachmentReader attachments;
    private final SettingsRepository settings;
    private final HttpClient http;
    private final ObjectMapper json;

    @Inject
    public GraphAccountantNotification(
            GraphConfiguration configuration,
            GraphOutlookConnection connection,
            InvoiceAttachmentReader attachments,
            SettingsRepository settings,
            ObjectMapper json) {
        this(
                configuration,
                connection,
                attachments,
                settings,
                json,
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    public GraphAccountantNotification(
            GraphConfiguration configuration,
            OutlookTokenProvider tokens,
            InvoiceAttachmentReader attachments,
            SettingsRepository settings,
            ObjectMapper json,
            HttpClient http) {
        this.configuration = configuration;
        this.tokens = tokens;
        this.attachments = attachments;
        this.settings = settings;
        this.json = json;
        this.http = http;
        if (http.followRedirects() != HttpClient.Redirect.NEVER)
            throw new IllegalArgumentException("Graph transport must not follow redirects.");
    }

    public NotifyAccountantResult notifyAccountant(InvoiceDocument document, ConfirmedInvoice confirmed) {
        var state = settings.initializeAndLoad();
        if (state.repairRequired())
            return result(
                    DeliveryOperationStatus.USER_ACTION_REQUIRED, "Repair delivery settings before sending email.");
        var configured = state.settings();
        Set<String> allowed = new HashSet<>();
        allowed.add(configured.accountant().recipient().toLowerCase(Locale.ROOT));
        configured.properties().stream()
                .filter(property -> property.enabled())
                .forEach(property -> allowed.add(
                        configured.accountantRecipient(property.id()).toLowerCase(Locale.ROOT)));
        if (!allowed.contains(confirmed.emailRecipient().toLowerCase(Locale.ROOT)))
            return result(
                    DeliveryOperationStatus.USER_ACTION_REQUIRED,
                    "The recipient is not in the configured accountant allowlist. Update Settings explicitly before sending.");
        if (document.sizeBytes() > GraphConfiguration.MAX_ATTACHMENT_BYTES)
            return result(
                    DeliveryOperationStatus.USER_ACTION_REQUIRED,
                    "Graph email requires a PDF smaller than 2.9 MB. No message was submitted.");
        String filename = confirmed.canonicalFilename();
        if (filename.contains("/")
                || filename.contains("\\")
                || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf"))
            return result(DeliveryOperationStatus.USER_ACTION_REQUIRED, "The confirmed PDF filename is invalid.");
        byte[] body;
        try {
            byte[] pdf = attachments.read(document);
            body = json.writeValueAsBytes(Map.of(
                    "saveToSentItems",
                    true,
                    "message",
                    Map.of(
                            "subject",
                            confirmed.emailSubject(),
                            "body",
                            Map.of("contentType", "Text", "content", confirmed.emailBody()),
                            "toRecipients",
                            List.of(Map.of("emailAddress", Map.of("address", confirmed.emailRecipient()))),
                            "attachments",
                            List.of(Map.of(
                                    "@odata.type",
                                    "#microsoft.graph.fileAttachment",
                                    "name",
                                    filename,
                                    "contentType",
                                    "application/pdf",
                                    "contentBytes",
                                    Base64.getEncoder().encodeToString(pdf))))));
        } catch (IOException | RuntimeException failure) {
            return result(
                    DeliveryOperationStatus.USER_ACTION_REQUIRED,
                    "The owned PDF attachment could not be verified. No message was submitted.");
        }
        if (body.length > GraphConfiguration.MAX_REQUEST_BYTES)
            return result(
                    DeliveryOperationStatus.USER_ACTION_REQUIRED,
                    "The encoded email exceeds Graph's small-request limit. Shorten the message or use a smaller PDF.");
        Optional<String> accessToken = tokens.accessToken();
        if (accessToken.isEmpty())
            return result(DeliveryOperationStatus.EXTERNAL_SESSION_REQUIRED, "Connect Outlook to submit this email.");
        String correlation = UUID.randomUUID().toString();
        var request = HttpRequest.newBuilder(configuration.endpoint())
                .timeout(configuration.timeout())
                .header("Authorization", "Bearer " + accessToken.orElseThrow())
                .header("Content-Type", "application/json")
                .header("client-request-id", correlation)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 202)
                return new NotifyAccountantResult(
                        DeliveryOperationStatus.SUCCEEDED,
                        "Accepted by Outlook for processing. Sent Items and recipient delivery are not verified.",
                        Optional.of("graph-request:"
                                + response.headers().firstValue("request-id").orElse(correlation)));
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                tokens.requireReconnect();
                return result(
                        DeliveryOperationStatus.EXTERNAL_SESSION_REQUIRED,
                        "Outlook authorization was rejected. Reconnect Outlook and check Mail.Send consent.");
            }
            if (response.statusCode() == 429)
                return result(
                        DeliveryOperationStatus.USER_ACTION_REQUIRED,
                        "Outlook is throttling requests. Wait before an explicit retry; no automatic resend occurs.");
            if (response.statusCode() >= 500) return uncertain();
            return result(
                    DeliveryOperationStatus.USER_ACTION_REQUIRED,
                    "Outlook rejected the email (HTTP " + response.statusCode() + "). No automatic resend occurs.");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return uncertain();
        } catch (IOException failure) {
            return uncertain();
        }
    }

    private static NotifyAccountantResult uncertain() {
        return result(
                DeliveryOperationStatus.SUBMISSION_UNCERTAIN,
                "Outlook may have accepted the message. Check Sent Items before deliberately issuing a new send; automatic retry is disabled.");
    }

    private static NotifyAccountantResult result(DeliveryOperationStatus status, String message) {
        return new NotifyAccountantResult(status, message, Optional.empty());
    }
}
