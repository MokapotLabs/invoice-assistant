package com.mokapotlabs.invoiceassistant.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.pipelineframework.connector.ConnectorPayloadOrigin;
import org.pipelineframework.repository.PayloadReference;

/** Stable semantic identity for a canonical payload reference. */
public final class PayloadReferenceIdentity {
    private PayloadReferenceIdentity() {}

    public static String of(PayloadReference reference) {
        Objects.requireNonNull(reference, "payload reference must not be null");
        StringBuilder canonical = new StringBuilder()
                .append(value(reference.provider()))
                .append('\n')
                .append(value(reference.container()))
                .append('\n')
                .append(value(reference.key()))
                .append('\n')
                .append(value(reference.checksum()))
                .append('\n')
                .append(reference.sizeBytes())
                .append('\n')
                .append(value(reference.version()))
                .append('\n');
        reference.connectorOrigin().ifPresent(origin -> appendOrigin(canonical, origin));
        return sha256(canonical.toString());
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void appendOrigin(StringBuilder canonical, ConnectorPayloadOrigin origin) {
        canonical
                .append(origin.bindingName().value())
                .append('\n')
                .append(origin.operation().providerId().value())
                .append('\n')
                .append(origin.operation().operationId())
                .append('\n')
                .append(origin.operation().kind())
                .append('\n')
                .append(origin.operation().majorVersion())
                .append('\n')
                .append(origin.providerMajorVersion())
                .append('\n')
                .append(origin.configuration()
                        .map(configuration -> configuration.digest())
                        .orElse(""));
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
