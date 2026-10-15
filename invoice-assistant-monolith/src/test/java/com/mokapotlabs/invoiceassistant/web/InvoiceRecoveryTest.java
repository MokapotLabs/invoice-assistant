package com.mokapotlabs.invoiceassistant.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.mokapotlabs.invoiceassistant.analysis.InvoiceExecutionTracker;
import com.mokapotlabs.invoiceassistant.app.ConfirmationResult;
import com.mokapotlabs.invoiceassistant.app.InvoiceAssistantFacade;
import com.mokapotlabs.invoiceassistant.interaction.PropertyConfirmationRequest;
import com.mokapotlabs.invoiceassistant.readside.InvoiceReadModel;
import com.mokapotlabs.invoiceassistant.storage.DeliveryOperationStatus;
import com.mokapotlabs.invoiceassistant.storage.InvoiceAnalysisStatus;
import com.mokapotlabs.invoiceassistant.support.FakeInvoiceLlmConnector;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.pipelineframework.PipelineExecutionService;
import org.pipelineframework.awaitable.AwaitCoordinator;
import org.pipelineframework.awaitable.AwaitInteractionRecord;
import org.pipelineframework.awaitable.AwaitInteractionStatus;
import org.pipelineframework.awaitable.AwaitPayloadSupport;

@QuarkusTest
@ResourceLock("invoice-assistant-data-root")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InvoiceRecoveryTest {
    private static final Path DATA_ROOT = Path.of(System.getProperty("java.io.tmpdir"), "invoice-assistant-oss-tests");

    @Inject
    InvoiceAssistantFacade facade;

    @Inject
    PipelineExecutionService pipeline;

    @Inject
    AwaitCoordinator awaits;

    @Inject
    InvoiceExecutionTracker executions;

    @BeforeEach
    @AfterEach
    void clearDataRoot() throws IOException {
        for (String directory : new String[] {"documents", "staging"}) {
            Path target = DATA_ROOT.resolve(directory);
            if (Files.exists(target)) {
                try (var paths = Files.walk(target)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(this::delete);
                }
            }
        }
    }

    @Test
    @Order(8)
    void successfulNotificationCanBeReissuedWithTheApprovedMessage() throws Exception {
        UUID documentId = admittedAndConfirmed("reissue.pdf", "reissue delivery");
        InvoiceReadModel firstDelivery = awaitRecord(
                documentId,
                record -> record.deliveryState().notification().status() == DeliveryOperationStatus.SUCCEEDED);
        assertEquals(1, firstDelivery.deliveryState().notification().attempts().size());

        given().cookie("invoice-local-session", PayloadUploadFixture.session())
                .when()
                .get("/invoices/" + documentId + "/delivery")
                .then()
                .statusCode(200)
                .body(containsString("Send invoice again"))
                .body(containsString("Reason for sending again"));

        given().cookie("invoice-local-session", PayloadUploadFixture.session())
                .header("HX-Request", "true")
                .formParam("reason", " ")
                .when()
                .post("/invoices/" + documentId + "/delivery/reissue")
                .then()
                .statusCode(409)
                .body(containsString("Enter a reason"));

        given().cookie("invoice-local-session", PayloadUploadFixture.session())
                .redirects()
                .follow(false)
                .formParam("reason", "Accountant requested another copy")
                .when()
                .post("/invoices/" + documentId + "/delivery/reissue")
                .then()
                .statusCode(303);

        InvoiceReadModel reissued = awaitRecord(
                documentId,
                record -> record.deliveryState().notification().attempts().size() == 2
                        && record.deliveryState().notification().status() == DeliveryOperationStatus.SUCCEEDED);
        assertEquals(2, reissued.deliveryState().notification().attempts().size());
        assertEquals(
                DeliveryOperationStatus.SUCCEEDED,
                reissued.deliveryState().notification().status());
    }

    @Test
    @Order(9)
    void confirmedInvoiceNeverOffersConfirmationAgainAndRepeatedPostDoesNotSendAgain() throws Exception {
        UUID documentId = admittedAndConfirmed("confirmation-refresh.pdf", "confirmed review refresh");
        var done = awaitRecord(
                documentId,
                record -> record.deliveryState().notification().status() == DeliveryOperationStatus.SUCCEEDED);
        given().cookie("invoice-local-session", PayloadUploadFixture.session())
                .get("/invoices/" + documentId + "/review")
                .then()
                .statusCode(200)
                .body(containsString("Continue to delivery"))
                .body(not(containsString("name=\"emailRecipient\"")));
        assertInstanceOf(
                ConfirmationResult.Accepted.class,
                facade.confirmProperty(
                        documentId,
                        "changed-property",
                        "Changed supplier",
                        "changed",
                        "different@example.test",
                        "Changed",
                        "Changed"));
        var unchanged = facade.review(documentId).orElseThrow();
        assertEquals(done.reviewState().confirmation(), unchanged.reviewState().confirmation());
        assertEquals(1, unchanged.deliveryState().notification().attempts().size());
    }

    @Test
    @Order(10)
    void expiredReviewReopensFromSavedEvidenceWithoutAnotherModelCall() throws Exception {
        UUID documentId = admitted("expired-review.pdf", "expired review evidence");
        var original =
                facade.review(documentId).orElseThrow().reviewState().review().orElseThrow();
        var pending = pendingInteraction(documentId);
        int modelCalls = FakeInvoiceLlmConnector.calls();
        var expired = awaits.markTimedOut(pending, pending.deadlineEpochMs() + 1)
                .await()
                .indefinitely()
                .orElseThrow();
        assertEquals(AwaitInteractionStatus.TIMED_OUT, expired.status());

        assertInstanceOf(
                ConfirmationResult.Accepted.class,
                facade.confirmProperty(
                        documentId,
                        original.recommendation().propertyId(),
                        "Approved supplier",
                        "Approved note",
                        "accountant@example.test",
                        "Approved expired invoice",
                        "Approved message"));
        var delivered = awaitRecord(
                documentId,
                record -> record.deliveryState().notification().status() == DeliveryOperationStatus.SUCCEEDED);
        assertEquals(original, delivered.reviewState().review().orElseThrow());
        assertEquals(
                ((PropertyConfirmationRequest) AwaitPayloadSupport.coercePayload(
                                pending.requestPayload(), PropertyConfirmationRequest.class))
                        .review()
                        .reviewId(),
                delivered.reviewState().confirmation().orElseThrow().reviewId());
        assertEquals(
                "Approved expired invoice",
                delivered.reviewState().confirmation().orElseThrow().emailSubject());
        assertEquals(modelCalls, FakeInvoiceLlmConnector.calls());
        assertEquals(1, delivered.deliveryState().notification().attempts().size());
    }

    @Test
    @Order(11)
    void deliveryRecoveryWithoutLiveExecutionRequiresAcknowledgement() throws Exception {
        UUID documentId = admittedAndConfirmed("notification-fail-once-restart.pdf", "delivery recovery");
        awaitRecord(
                documentId, record -> record.deliveryState().notification().status() == DeliveryOperationStatus.FAILED);
        // Lose only the disposable process projection, as happens on restart. Persisted approval remains.
        executions.completed(documentId, InvoiceExecutionTracker.Phase.DELIVERY, "discarded-observation", 0);
        given().cookie("invoice-local-session", PayloadUploadFixture.session())
                .get("/invoices/" + documentId + "/delivery")
                .then()
                .statusCode(200)
                .body(containsString("acknowledgePossibleDuplicate"));
        given().cookie("invoice-local-session", PayloadUploadFixture.session())
                .post("/invoices/" + documentId + "/delivery/retry")
                .then()
                .statusCode(409)
                .body(containsString("acknowledge a possible duplicate"));
        given().cookie("invoice-local-session", PayloadUploadFixture.session())
                .redirects()
                .follow(false)
                .formParam("acknowledgePossibleDuplicate", "true")
                .post("/invoices/" + documentId + "/delivery/retry")
                .then()
                .statusCode(303);
        var delivered = awaitRecord(
                documentId,
                record -> record.deliveryState().notification().status() == DeliveryOperationStatus.SUCCEEDED);
        assertEquals(1, delivered.deliveryState().notification().attempts().size());
    }

    private AwaitInteractionRecord pendingInteraction(UUID documentId) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(5);
        while (Instant.now().isBefore(deadline)) {
            var pending = pipeline
                    .queryPendingAwaitInteractions("local-invoice-assistant", "", "", "", 100)
                    .await()
                    .indefinitely()
                    .stream()
                    .filter(candidate -> ((PropertyConfirmationRequest) AwaitPayloadSupport.coercePayload(
                                    candidate.requestPayload(), PropertyConfirmationRequest.class))
                            .review()
                            .documentId()
                            .equals(documentId))
                    .findFirst();
            if (pending.isPresent()) {
                Thread.sleep(150);
                return pending.orElseThrow();
            }
            Thread.sleep(25);
        }
        throw new AssertionError("No pending review for " + documentId);
    }

    private UUID admittedAndConfirmed(String filename, String contents) throws Exception {
        UUID documentId = admitted(filename, contents);
        String propertyId = facade.review(documentId)
                .orElseThrow()
                .reviewState()
                .review()
                .orElseThrow()
                .recommendation()
                .propertyId();
        assertInstanceOf(
                ConfirmationResult.Accepted.class,
                facade.confirmProperty(
                        documentId,
                        propertyId,
                        "Fixture supplier",
                        "A memorable fixture invoice.",
                        "accountant@example.test",
                        "Invoice ready for delivery",
                        "The confirmed invoice is attached."));
        awaitRecord(documentId, record -> record.reviewState().confirmation().isPresent());
        return documentId;
    }

    private UUID admitted(String filename, String contents) throws Exception {
        String location = given().cookie("invoice-local-session", PayloadUploadFixture.session())
                .redirects()
                .follow(false)
                .cookie("invoice-local-session", PayloadUploadFixture.session())
                .contentType("application/json")
                .body(PayloadUploadFixture.uploads(
                        PayloadUploadFixture.file(filename, searchablePdf(contents), "application/pdf")))
                .when()
                .post("/invoices")
                .then()
                .statusCode(303)
                .extract()
                .header("Location");
        UUID documentId = UUID.fromString(
                URI.create(location).getPath().replace("/invoices/", "").replace("/review", ""));
        awaitRecord(documentId, record -> record.reviewState().status() == InvoiceAnalysisStatus.REVIEW_READY);
        return documentId;
    }

    private byte[] searchablePdf(String contents) throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(contents);
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private InvoiceReadModel awaitRecord(UUID documentId, Predicate<InvoiceReadModel> condition)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            var record = facade.review(documentId);
            if (record.isPresent() && condition.test(record.orElseThrow())) {
                return record.orElseThrow();
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Invoice did not reach the requested delivery state: " + documentId);
    }

    private void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
