package com.mokapotlabs.invoiceassistant.web;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import com.mokapotlabs.invoiceassistant.app.InvoiceAssistantFacade;
import com.mokapotlabs.invoiceassistant.settings.ApplicationSettings;
import com.mokapotlabs.invoiceassistant.settings.SettingsRepository;
import com.mokapotlabs.invoiceassistant.storage.DeliveryOperationStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.*;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@QuarkusTest
@ResourceLock("invoice-assistant-data-root")
class InvoiceAutoApprovalTest {
    @Inject
    InvoiceAssistantFacade facade;

    @Inject
    SettingsRepository settings;

    @Test
    void optInDeliversClearInvoiceWhileUncertainInvoiceWaitsForReview() throws Exception {
        ApplicationSettings previous = settings.initializeAndLoad().settings();
        try {
            settings.save(withAutoApproval(previous, true));
            UUID clear = upload("auto-clear-" + UUID.randomUUID() + ".pdf");
            await(clear, true);
            String modelInput = com.mokapotlabs.invoiceassistant.support.FakeInvoiceLlmConnector.lastRequest()
                    .applicationStateJson();
            assertFalse(modelInput.contains("approvalPolicy"));
            assertFalse(modelInput.contains("accountant@example.test"));
            UUID uncertain = upload("uncertain-" + UUID.randomUUID() + ".pdf");
            await(uncertain, false);
        } finally {
            settings.save(previous);
        }
    }

    @Test
    void defaultRequiresReviewEvenWithClearInvoiceEvidence() throws Exception {
        ApplicationSettings previous = settings.initializeAndLoad().settings();
        try {
            settings.save(withAutoApproval(previous, false));
            await(upload("auto-clear-opt-out-" + UUID.randomUUID() + ".pdf"), false);
        } finally {
            settings.save(previous);
        }
    }

    private UUID upload(String filename) throws Exception {
        String location = given().cookie("invoice-local-session", PayloadUploadFixture.session())
                .contentType("application/json")
                .body(PayloadUploadFixture.uploads(PayloadUploadFixture.file(filename, pdf(), "application/pdf")))
                .redirects()
                .follow(false)
                .post("/invoices")
                .then()
                .statusCode(303)
                .extract()
                .header("Location");
        return UUID.fromString(URI.create(location).getPath().split("/")[2]);
    }

    private void await(UUID id, boolean automatic) throws Exception {
        Instant deadline = Instant.now().plusSeconds(15);
        while (Instant.now().isBefore(deadline)) {
            var record = facade.review(id);
            if (record.isPresent()
                    && record.orElseThrow().reviewState().review().isPresent()) {
                var invoice = record.orElseThrow();
                if (!automatic) {
                    Thread.sleep(300);
                    assertTrue(facade.review(id)
                            .orElseThrow()
                            .reviewState()
                            .confirmation()
                            .isEmpty());
                    assertEquals(
                            DeliveryOperationStatus.NOT_STARTED,
                            invoice.deliveryState().notification().status());
                    return;
                }
                if (invoice.deliveryState().notification().status() == DeliveryOperationStatus.SUCCEEDED) {
                    assertTrue(invoice.reviewState().confirmation().isPresent());

                    return;
                }
            }
            Thread.sleep(25);
        }
        fail("Invoice did not reach the expected approval/delivery state: " + id + " " + facade.review(id));
    }

    private static ApplicationSettings withAutoApproval(ApplicationSettings value, boolean enabled) {
        return new ApplicationSettings(
                value.schemaVersion(), value.filenameSeparator(), value.properties(), value.accountant(), enabled);
    }

    static byte[] pdf() throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream text = new PDPageContentStream(document, page)) {
                text.beginText();
                text.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
                text.newLineAtOffset(30, 720);
                text.showText("Fixture supplier FIXTURE-1001 Total 1059.38 17 Maple Street, Exampletown");
                text.newLineAtOffset(0, -15);
                text.showText(UUID.randomUUID().toString());
                text.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
