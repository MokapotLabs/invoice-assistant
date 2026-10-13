package com.mokapotlabs.invoiceassistant.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mokapotlabs.invoiceassistant.app.*;
import com.mokapotlabs.invoiceassistant.readside.InvoiceAdmissionQuery;
import com.mokapotlabs.invoiceassistant.settings.SettingsRepository;
import com.mokapotlabs.invoiceassistant.web.InvoicePayloadAuthorizer;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pipelineframework.config.boundary.*;
import org.pipelineframework.connector.*;
import org.pipelineframework.connector.objectingest.FilesystemObjectConnector;
import org.pipelineframework.repository.PayloadReference;

class FilesystemInvoiceDocumentStoreTest {
    @TempDir
    Path directory;

    @Test
    void admitsOwnedReferenceRejectsDuplicateAndPreservesReusedObject() throws Exception {
        var fixture = fixture();
        var reference = fixture.upload("%PDF-1.7\nInvoice body");
        var result = assertInstanceOf(
                UploadResult.Accepted.class,
                fixture.store().admit(new UploadedInvoice(reference, "invoice.pdf"), System.getProperty("user.name")));
        assertEquals(reference, result.document().payloadReference());
        assertEquals("local", reference.metadata().get("tpf.payload.owner.tenant"));
        when(fixture.admissions().findDocumentBySha256(reference.checksum()))
                .thenReturn(Optional.of(result.document()));
        var second = fixture.upload("%PDF-1.7\nInvoice body");
        assertInstanceOf(
                UploadResult.Duplicate.class,
                fixture.store().admit(new UploadedInvoice(second, "invoice.pdf"), System.getProperty("user.name")));
        assertFalse(Files.exists(directory.resolve(second.key())));
        assertInstanceOf(
                UploadResult.Duplicate.class,
                fixture.store().admit(new UploadedInvoice(reference, "invoice.pdf"), System.getProperty("user.name")));
        assertTrue(Files.exists(directory.resolve(reference.key())));
    }

    @Test
    void rejectedPdfIsRemovedButWrongOwnerCannotRemovePayload() throws Exception {
        var fixture = fixture();
        var invalid = fixture.upload("not a PDF");
        assertInstanceOf(
                UploadResult.Rejected.class,
                fixture.store().admit(new UploadedInvoice(invalid, "invoice.pdf"), System.getProperty("user.name")));
        assertFalse(Files.exists(directory.resolve(invalid.key())));
        var valid = fixture.upload("%PDF-1.7\nInvoice body");
        assertInstanceOf(
                UploadResult.Rejected.class,
                fixture.store().admit(new UploadedInvoice(valid, "../invoice.pdf"), "unauthorised"));
        assertTrue(Files.exists(directory.resolve(valid.key())));
        assertInstanceOf(
                UploadResult.Rejected.class,
                fixture.store().admit(new UploadedInvoice(valid, "../invoice.pdf"), System.getProperty("user.name")));
        assertFalse(Files.exists(directory.resolve(valid.key())));
    }

    private Fixture fixture() throws Exception {
        Files.createDirectories(directory);
        var registry = ConnectorBindingRegistry.fromProviders(
                List.of(new ConnectorBindingDefinition(
                        ConnectorBindingName.of("invoice-documents"),
                        new FilesystemObjectConnector().id(),
                        1,
                        ConnectorConfigurationDocument.empty())),
                List.of(new FilesystemObjectConnector()));
        var runtime = ConnectorRuntimeContext.empty();
        var authorizer = new InvoicePayloadAuthorizer();
        var admissions = mock(InvoiceAdmissionQuery.class);
        when(admissions.findDocumentBySha256(anyString())).thenReturn(Optional.empty());
        var configuration = new InvoiceAssistantConfiguration(directory, 1024);
        var store = new FilesystemInvoiceDocumentStore(
                configuration,
                admissions,
                new UploadAdmissionLock(),
                registry,
                runtime,
                new SettingsRepository(directory, "/defaults/settings.yaml"),
                authorizer);
        return new Fixture(store, admissions, new OwnedPayloadTransfer(registry, runtime, authorizer), directory);
    }

    private record Fixture(
            FilesystemInvoiceDocumentStore store,
            InvoiceAdmissionQuery admissions,
            OwnedPayloadTransfer transfer,
            Path root) {
        PayloadReference upload(String bytes) throws Exception {
            var boundary = new PipelineHttpPayloadBoundaryConfig(
                    "invoice-file",
                    PipelineHttpPayloadBoundaryConfig.Direction.UPLOAD,
                    "invoice-document-store",
                    "UploadedInvoice",
                    "invoice",
                    List.of("application/pdf"),
                    1024,
                    "invoice.write");
            var target = new PipelineObjectPublishConfig(
                    "invoice-document-store",
                    "object",
                    "filesystem",
                    Optional.of("invoice-documents"),
                    Map.of("root", root.toString()),
                    PipelineObjectNamingConfig.defaults(),
                    PipelineObjectPublishPayloadConfig.defaults(),
                    PipelineObjectPublishGroupingConfig.defaults());
            return transfer.upload(
                    boundary,
                    target,
                    System.getProperty("user.name"),
                    "local",
                    "invoices",
                    "application/pdf",
                    new ByteArrayInputStream(bytes.getBytes()));
        }
    }
}
