package com.mokapotlabs.invoiceassistant.domain;

import java.nio.file.Path;
import java.util.Objects;

/** Authored file-transform result; TPF publishes the image and carries canonical context. */
public record RasterizedSupplierHeader(Path image) {
    public RasterizedSupplierHeader {
        Objects.requireNonNull(image, "image must not be null");
    }
}
