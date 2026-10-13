package com.mokapotlabs.invoiceassistant.readside;

import com.mokapotlabs.invoiceassistant.domain.InvoiceDocument;
import com.mokapotlabs.invoiceassistant.domain.InvoiceFiles;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.pipelineframework.proto.PayloadReferenceProtobufCodec;

/** Typed admission read side used by the UI and separate analysis redrives. */
@ApplicationScoped
public class InvoiceAdmissionQuery {
    private final EntityManager entityManager;
    private final InvoiceAdmissionPersistenceMapper mapper;

    public InvoiceAdmissionQuery(EntityManager entityManager, InvoiceAdmissionPersistenceMapper mapper) {
        this.entityManager = entityManager;
        this.mapper = mapper;
    }

    public List<InvoiceAdmissionEntity> all() {
        return entityManager
                .createQuery("from InvoiceAdmissionEntity order by observedAt desc", InvoiceAdmissionEntity.class)
                .getResultList();
    }

    public Optional<InvoiceAdmissionEntity> find(UUID documentId) {
        return entityManager
                .createQuery(
                        "from InvoiceAdmissionEntity where documentId = :documentId order by observedAt desc",
                        InvoiceAdmissionEntity.class)
                .setParameter("documentId", documentId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    public Optional<InvoiceDocument> findDocumentBySha256(String sha256) {
        return entityManager
                .createQuery(
                        "from InvoiceAdmissionEntity where sha256 = :sha256 order by observedAt",
                        InvoiceAdmissionEntity.class)
                .setParameter("sha256", sha256)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .map(this::document);
    }

    public InvoiceFiles files(InvoiceAdmissionEntity entity) {
        return mapper.fromExternal(entity);
    }

    public InvoiceDocument document(InvoiceAdmissionEntity entity) {
        return new InvoiceDocument(
                entity.documentId,
                entity.originalFilename,
                entity.mediaType,
                entity.sizeBytes,
                entity.sha256,
                entity.observedAt,
                PayloadReferenceProtobufCodec.decode(entity.invoiceReference));
    }
}
