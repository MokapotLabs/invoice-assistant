package com.mokapotlabs.invoiceassistant.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** Pure, bounded PDFBox extraction for born-digital invoices. */
public final class PdfInvoiceTextExtractor {
    private static final long MAIN_MEMORY_BYTES = 8L * 1024 * 1024;

    public String extract(Path pdf, int maxPages, int maxCharacters, long maxScratchBytes) {
        try {
            return extract(Files.readAllBytes(pdf), maxPages, maxCharacters, maxScratchBytes);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invoice PDF could not be read", exception);
        }
    }

    public String extract(byte[] pdfBytes, int maxPages, int maxCharacters, long maxScratchBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF bytes must not be empty");
        }
        if (maxPages < 1 || maxCharacters < 1 || maxScratchBytes < 1) {
            throw new IllegalArgumentException("PDF extraction limits must be positive");
        }
        MemoryUsageSetting memory = MemoryUsageSetting.setupMixed(MAIN_MEMORY_BYTES, maxScratchBytes);
        try (RandomAccessReadBuffer source = new RandomAccessReadBuffer(pdfBytes);
                PDDocument document = Loader.loadPDF(source, memory.streamCache)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount < 1 || pageCount > maxPages) {
                throw new IllegalArgumentException("PDF page count is outside the configured limit");
            }
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("Encrypted PDFs cannot be analysed");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document).strip();
            if (text.length() > maxCharacters) {
                throw new IllegalArgumentException("PDF text exceeds the configured character limit");
            }
            return text;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invoice PDF text could not be extracted", exception);
        }
    }
}
