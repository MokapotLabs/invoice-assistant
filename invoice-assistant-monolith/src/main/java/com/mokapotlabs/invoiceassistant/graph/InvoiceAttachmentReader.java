package com.mokapotlabs.invoiceassistant.graph;

import com.mokapotlabs.invoiceassistant.app.InvoiceAssistantConfiguration;
import com.mokapotlabs.invoiceassistant.domain.InvoiceDocument;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.pipelineframework.config.boundary.*;
import org.pipelineframework.connector.*;

/** Verifies the owned provider capability and checksum before materializing a bounded attachment. */
@ApplicationScoped
public class InvoiceAttachmentReader {
    private final InvoiceAssistantConfiguration configuration;
    private final OwnedPayloadTransfer transfer;

    @Inject
    public InvoiceAttachmentReader(
            InvoiceAssistantConfiguration configuration,
            ConnectorBindingRegistry bindings,
            ConnectorRuntimeContext runtime,
            PayloadBoundaryAuthorizer authorizer) {
        this.configuration = configuration;
        transfer = new OwnedPayloadTransfer(bindings, runtime, authorizer);
    }

    public byte[] read(InvoiceDocument document) throws java.io.IOException {
        var reference = document.payloadReference();
        if (document.sizeBytes() != reference.sizeBytes()
                || !document.sha256().equals(reference.checksum())
                || !"application/pdf".equals(reference.contentType()))
            throw new IllegalArgumentException("PDF reference does not match the admitted document.");
        if (document.sizeBytes() > GraphConfiguration.MAX_ATTACHMENT_BYTES)
            throw new IllegalArgumentException("PDF exceeds the small-attachment limit.");
        var boundary = new PipelineHttpPayloadBoundaryConfig(
                "invoice-file-download",
                PipelineHttpPayloadBoundaryConfig.Direction.DOWNLOAD,
                "invoice-document-store",
                "UploadedInvoice",
                "invoice",
                List.of("application/pdf"),
                0,
                "invoice.read");
        var source = new PipelineObjectSourceConfig(
                "invoice-document-store",
                "object",
                "filesystem",
                Optional.of("invoice-documents"),
                Map.of("root", configuration.dataRoot().toString()),
                PipelineObjectFilterConfig.defaults(),
                PipelineObjectPollConfig.defaults(),
                PipelineObjectIdentityConfig.defaults(),
                PipelineObjectPayloadConfig.reference());
        try (var bytes = new ByteArrayOutputStream() {
            @Override public synchronized void write(byte[] value, int offset, int length) {
                if ((long) size() + length > GraphConfiguration.MAX_ATTACHMENT_BYTES)
                    throw new IllegalArgumentException("PDF attachment exceeds the bounded read limit.");
                super.write(value, offset, length);
            }
            @Override public synchronized void write(int value) {
                if (size() >= GraphConfiguration.MAX_ATTACHMENT_BYTES)
                    throw new IllegalArgumentException("PDF attachment exceeds the bounded read limit.");
                super.write(value);
            }
        }) {
            transfer.download(
                    boundary,
                    source,
                    System.getProperty("user.name"),
                    "local",
                    "invoices",
                    document.payloadReference(),
                    bytes);
            return bytes.toByteArray();
        }
    }
}
