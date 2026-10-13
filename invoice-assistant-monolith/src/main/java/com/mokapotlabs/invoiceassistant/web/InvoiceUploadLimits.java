package com.mokapotlabs.invoiceassistant.web;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record InvoiceUploadLimits(int maxFiles, long maxFileBytes, long maxBatchBytes) {
    public InvoiceUploadLimits {
        if (maxFiles < 1 || maxFileBytes < 1 || maxBatchBytes < maxFileBytes) {
            throw new IllegalArgumentException("invoice upload limits must be positive and internally consistent");
        }
    }

    public Optional<String> validateBatch(List<Long> fileSizes) {
        List<Long> sizes = Objects.requireNonNull(fileSizes, "fileSizes must not be null");
        if (sizes.isEmpty()) {
            return Optional.of("Choose at least one PDF to upload.");
        }
        if (sizes.size() > maxFiles) {
            return Optional.of("Choose no more than " + maxFiles + " PDFs at once.");
        }
        long total = 0;
        for (Long size : sizes) {
            if (size == null || size < 0) {
                return Optional.of("One of the selected PDFs has an invalid size.");
            }
            if (size > maxFileBytes) {
                return Optional.of("One selected PDF exceeds the " + maxFileMegabytes() + " MB file limit.");
            }
            if (size > maxBatchBytes - total) {
                return Optional.of("The selected PDFs exceed the " + maxBatchMegabytes() + " MB batch limit.");
            }
            total += size;
        }
        return Optional.empty();
    }

    public long maxFileMegabytes() {
        return maxFileBytes / (1024 * 1024);
    }

    public long maxBatchMegabytes() {
        return maxBatchBytes / (1024 * 1024);
    }
}
