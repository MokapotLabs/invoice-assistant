package com.mokapotlabs.invoiceassistant.readside;

import com.mokapotlabs.invoiceassistant.domain.InvoiceFiles;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.UUID;
import org.pipelineframework.mapper.Mapper;
import org.pipelineframework.proto.PayloadReferenceProtobufCodec;

/** Stores both canonical payload references without exposing their framework-owned structure. */
@ApplicationScoped
public class InvoiceAdmissionPersistenceMapper implements Mapper<InvoiceFiles, InvoiceAdmissionEntity> {
    @Override
    public InvoiceFiles fromExternal(InvoiceAdmissionEntity entity) {
        return new InvoiceFiles(
                entity.documentId,
                entity.originalFilename,
                PayloadReferenceProtobufCodec.decode(entity.invoiceReference),
                PayloadReferenceProtobufCodec.decode(entity.catalogueReference),
                PayloadReferenceProtobufCodec.decode(entity.invoiceReference),
                entity.invoiceSourceIdentity,
                entity.catalogueSourceIdentity);
    }

    @Override
    public InvoiceAdmissionEntity toExternal(InvoiceFiles files) {
        var entity = new InvoiceAdmissionEntity();
        entity.admissionId = UUID.randomUUID();
        entity.documentId = files.documentId();
        entity.observedAt = Instant.now();
        entity.originalFilename = files.originalFilename();
        entity.mediaType = files.invoice().contentType() == null
                ? "application/pdf"
                : files.invoice().contentType();
        entity.sizeBytes = files.invoice().sizeBytes();
        entity.sha256 = required(files.invoice().checksum(), "invoice payload checksum");
        entity.invoiceReference = PayloadReferenceProtobufCodec.encode(files.invoice());
        entity.catalogueReference = PayloadReferenceProtobufCodec.encode(files.catalogue());
        entity.invoiceSourceIdentity = files.invoiceSourceIdentity();
        entity.catalogueSourceIdentity = files.catalogueSourceIdentity();
        return entity;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}
