package com.mokapotlabs.invoiceassistant.analysis.pipeline;

import com.mokapotlabs.invoiceassistant.domain.MaterializedVisionRasterizationRequest;
import com.mokapotlabs.invoiceassistant.domain.RasterizedSupplierHeader;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.RenderDestination;
import org.pipelineframework.service.blocking.BlockingService;

/** Deterministically renders only the first-page supplier/header region for the vision fallback. */
@ApplicationScoped
@Unremovable
public final class RasterizeSupplierHeaderStep
        implements BlockingService<MaterializedVisionRasterizationRequest, RasterizedSupplierHeader> {
    static final float DPI = 144f;
    static final float HEADER_FRACTION = 0.40f;
    static final int MAX_WIDTH_PIXELS = 1800;

    @Override
    public RasterizedSupplierHeader processBlocking(MaterializedVisionRasterizationRequest request) {
        return rasterize(request);
    }

    RasterizedSupplierHeader rasterize(MaterializedVisionRasterizationRequest request) {
        Path image = request.invoice().resolveSibling("supplier-header.png");
        try (PDDocument document = Loader.loadPDF(request.invoice().toFile())) {
            if (document.getNumberOfPages() < 1) {
                throw new IllegalArgumentException("Invoice PDF has no pages to inspect");
            }
            PDRectangle crop = document.getPage(0).getCropBox();
            float requestedScale = DPI / 72f;
            float scale = Math.min(requestedScale, MAX_WIDTH_PIXELS / crop.getWidth());
            BufferedImage page = new PDFRenderer(document).renderImage(0, scale, ImageType.RGB, RenderDestination.VIEW);
            int headerHeight = Math.max(1, (int) Math.ceil(page.getHeight() * HEADER_FRACTION));
            BufferedImage header = page.getSubimage(0, 0, page.getWidth(), headerHeight);
            if (!ImageIO.write(header, "png", image.toFile())) {
                throw new IllegalStateException("PNG image writer is unavailable");
            }
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invoice supplier header could not be rasterized", failure);
        }
        return new RasterizedSupplierHeader(image);
    }
}
