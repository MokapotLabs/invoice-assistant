package com.mokapotlabs.invoiceassistant.storage;

import com.mokapotlabs.invoiceassistant.app.InvoiceAssistantConfiguration;
import com.mokapotlabs.invoiceassistant.app.UploadAdmissionLock;
import com.mokapotlabs.invoiceassistant.app.UploadResult;
import com.mokapotlabs.invoiceassistant.app.UploadedInvoice;
import com.mokapotlabs.invoiceassistant.domain.InvoiceDocument;
import com.mokapotlabs.invoiceassistant.domain.InvoiceFiles;
import com.mokapotlabs.invoiceassistant.readside.InvoiceAdmissionQuery;
import com.mokapotlabs.invoiceassistant.settings.SettingsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.pipelineframework.config.boundary.PipelineHttpPayloadBoundaryConfig;
import org.pipelineframework.config.boundary.PipelineObjectNamingConfig;
import org.pipelineframework.config.boundary.PipelineObjectPublishConfig;
import org.pipelineframework.config.boundary.PipelineObjectPublishGroupingConfig;
import org.pipelineframework.config.boundary.PipelineObjectPublishPayloadConfig;
import org.pipelineframework.connector.ConnectorBindingName;
import org.pipelineframework.connector.ConnectorBindingRegistry;
import org.pipelineframework.connector.ConnectorOperationKind;
import org.pipelineframework.connector.ConnectorRuntimeContext;
import org.pipelineframework.connector.OwnedPayloadTransfer;
import org.pipelineframework.objectpublish.ObjectTargetProvider;
import org.pipelineframework.objectpublish.ObjectWriteCloseRequest;
import org.pipelineframework.objectpublish.ObjectWriteOpenRequest;
import org.pipelineframework.objectpublish.ObjectWriteResult;
import org.pipelineframework.objectpublish.ObjectWriteSession;
import org.pipelineframework.repository.PayloadReference;

@ApplicationScoped
public class FilesystemInvoiceDocumentStore {
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final String TARGET_NAME = "invoice-document-store";
    private static final ConnectorBindingName DOCUMENTS_BINDING = new ConnectorBindingName("invoice-documents");

    private final InvoiceAssistantConfiguration configuration;
    private final InvoiceAdmissionQuery admissions;
    private final UploadAdmissionLock admissionLock;
    private final ConnectorBindingRegistry connectorBindings;
    private final ConnectorRuntimeContext connectorRuntimeContext;
    private final OwnedPayloadTransfer payloadTransfer;
    private final PdfAdmissionValidator pdfAdmissionValidator;
    private final SettingsRepository settingsRepository;

    @Inject
    public FilesystemInvoiceDocumentStore(
            InvoiceAssistantConfiguration configuration,
            InvoiceAdmissionQuery admissions,
            UploadAdmissionLock admissionLock,
            ConnectorBindingRegistry connectorBindings,
            ConnectorRuntimeContext connectorRuntimeContext,
            SettingsRepository settingsRepository,
            org.pipelineframework.connector.PayloadBoundaryAuthorizer authorizer) {
        this.configuration = Objects.requireNonNull(configuration);
        this.admissions = Objects.requireNonNull(admissions);
        this.admissionLock = Objects.requireNonNull(admissionLock);
        this.connectorBindings = Objects.requireNonNull(connectorBindings);
        this.connectorRuntimeContext = Objects.requireNonNull(connectorRuntimeContext);
        this.pdfAdmissionValidator = new PdfAdmissionValidator(configuration);
        this.settingsRepository = Objects.requireNonNull(settingsRepository);
        this.payloadTransfer = new OwnedPayloadTransfer(
                connectorBindings, connectorRuntimeContext, Objects.requireNonNull(authorizer));
    }

    public UploadResult admit(UploadedInvoice upload, String principal) {
        PayloadReference reference = upload.invoice();
        Optional<Path> unadmitted = Optional.empty();
        admissionLock.lock();
        try {
            var boundary = new PipelineHttpPayloadBoundaryConfig(
                    "invoice-file-download",
                    PipelineHttpPayloadBoundaryConfig.Direction.DOWNLOAD,
                    TARGET_NAME,
                    "UploadedInvoice",
                    "invoice",
                    List.of("application/pdf"),
                    0,
                    "invoice.read");
            var source = new org.pipelineframework.config.boundary.PipelineObjectSourceConfig(
                    TARGET_NAME,
                    "object",
                    "filesystem",
                    Optional.of(DOCUMENTS_BINDING.value()),
                    Map.of("root", configuration.dataRoot().toString()),
                    org.pipelineframework.config.boundary.PipelineObjectFilterConfig.defaults(),
                    org.pipelineframework.config.boundary.PipelineObjectPollConfig.defaults(),
                    org.pipelineframework.config.boundary.PipelineObjectIdentityConfig.defaults(),
                    org.pipelineframework.config.boundary.PipelineObjectPayloadConfig.reference());

            // Opening verifies the signed provider capability; streaming verifies length and checksum.
            payloadTransfer.download(
                    boundary,
                    source,
                    principal,
                    "local",
                    "invoices",
                    reference,
                    java.io.OutputStream.nullOutputStream());
            Path path = controlledPath(reference.key());
            Optional<InvoiceDocument> existing = admissions.findDocumentBySha256(reference.checksum());
            if (existing.isPresent()) {
                if (!existing.orElseThrow().payloadReference().key().equals(reference.key()))
                    Files.deleteIfExists(path);
                return new UploadResult.Duplicate(existing.orElseThrow());
            }
            unadmitted = Optional.of(path);
            String filename = UploadFilenameValidator.validate(upload.originalFilename(), reference.contentType());
            if (reference.sizeBytes() > configuration.maxUploadBytes()) {
                throw new UploadValidationException("The PDF exceeds the configured upload size limit.");
            }
            try (InputStream input = Files.newInputStream(path)) {
                if (!Arrays.equals(PDF_SIGNATURE, input.readNBytes(PDF_SIGNATURE.length))) {
                    throw new UploadValidationException("The uploaded file does not have a PDF signature.");
                }
            }
            pdfAdmissionValidator.validate(path);
            UUID documentId = UUID.randomUUID();
            InvoiceDocument document = new InvoiceDocument(
                    documentId,
                    filename,
                    reference.contentType(),
                    reference.sizeBytes(),
                    reference.checksum(),
                    Instant.now(),
                    reference);
            return new UploadResult.Accepted(
                    document, new InvoiceFiles(documentId, filename, reference, publishCatalogueSnapshot(documentId)));
        } catch (SecurityException exception) {
            return new UploadResult.Rejected("The invoice payload reference is not authorised.");
        } catch (UploadValidationException exception) {
            cleanupPublished(unadmitted, exception);
            return new UploadResult.Rejected(exception.getMessage());
        } catch (Exception exception) {
            cleanupPublished(unadmitted, exception);
            return new UploadResult.Failed("The invoice payload could not be admitted.");
        } finally {
            admissionLock.unlock();
        }
    }

    /** Creates a new immutable analysis admission fact using the current catalogue snapshot. */
    public InvoiceFiles workPackage(InvoiceDocument document) throws IOException {
        PayloadReference catalogue = publishCatalogueSnapshot(document.documentId());
        return new InvoiceFiles(
                document.documentId(), document.originalFilename(), document.payloadReference(), catalogue);
    }

    private PayloadReference publishCatalogueSnapshot(UUID documentId) throws IOException {
        var settings = settingsRepository.initializeAndLoad();
        if (settings.repairRequired()) {
            throw new IllegalStateException(settings.message());
        }
        byte[] bytes;
        synchronized (settingsRepository) {
            var state = settingsRepository.initializeAndLoad();
            var yaml = new org.yaml.snakeyaml.Yaml(
                    new org.yaml.snakeyaml.constructor.SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()));
            Map<String, Object> snapshot = yaml.load(Files.readString(settingsRepository.path()));
            var configured = state.settings();
            snapshot.put(
                    "autoApprovalPolicy",
                    Map.of(
                            "enabled",
                            configured.autoApprove(),
                            "admittedAt",
                            Instant.now().toString(),
                            "subjectTemplate",
                            configured.accountant().subjectTemplate(),
                            "bodyTemplate",
                            configured.accountant().bodyTemplate(),
                            "deliveries",
                            configured.properties().stream()
                                    .map(property -> Map.of(
                                            "propertyId",
                                            property.id(),
                                            "recipient",
                                            configured.accountantRecipient(property.id()),
                                            "ready",
                                            property.deliveryReady()
                                                    && configured.accountant().isReady()))
                                    .toList()));
            bytes = new org.yaml.snakeyaml.Yaml().dump(snapshot).getBytes(StandardCharsets.UTF_8);
        }
        String checksum = HexFormat.of().formatHex(sha256().digest(bytes));
        String key = "documents/" + documentId + "/catalogues/" + checksum + ".yaml";
        ObjectWriteSession session = targetProvider()
                .open(new ObjectWriteOpenRequest(
                        TARGET_NAME,
                        target(),
                        key,
                        "application/yaml",
                        Map.of("documentId", documentId.toString(), "role", "property-catalogue"),
                        "catalogue:" + documentId + ":" + checksum))
                .toCompletableFuture()
                .join();
        try {
            session.write(ByteBuffer.wrap(bytes)).toCompletableFuture().join();
            ObjectWriteResult result = session.close(new ObjectWriteCloseRequest(
                            bytes.length,
                            checksum,
                            Map.of("documentId", documentId.toString(), "role", "property-catalogue")))
                    .toCompletableFuture()
                    .join();
            return connectorBindings.ownPayloadReference(DOCUMENTS_BINDING, "filesystem", 1, result.reference());
        } catch (RuntimeException failure) {
            session.abort(failure).toCompletableFuture().join();
            throw failure;
        }
    }

    private PipelineObjectPublishConfig target() {
        return new PipelineObjectPublishConfig(
                TARGET_NAME,
                "object",
                "filesystem",
                Optional.of(DOCUMENTS_BINDING.value()),
                Map.of("root", configuration.dataRoot().toString()),
                PipelineObjectNamingConfig.defaults(),
                PipelineObjectPublishPayloadConfig.defaults(),
                PipelineObjectPublishGroupingConfig.defaults());
    }

    private ObjectTargetProvider targetProvider() {
        connectorBindings
                .activate(DOCUMENTS_BINDING, connectorRuntimeContext)
                .toCompletableFuture()
                .join();
        return (ObjectTargetProvider) connectorBindings.requireOperation(
                DOCUMENTS_BINDING, "filesystem", ConnectorOperationKind.OBJECT_TARGET, 1);
    }

    private Path controlledPath(String key) {
        Path path = configuration.dataRoot().resolve(key).normalize();
        if (!path.startsWith(configuration.dataRoot())) {
            throw new SecurityException("Document path escapes the configured data root");
        }
        return path;
    }

    private static void cleanupPublished(Optional<Path> path, Exception failure) {
        path.ifPresent(published -> {
            try {
                Files.deleteIfExists(published);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        });
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
