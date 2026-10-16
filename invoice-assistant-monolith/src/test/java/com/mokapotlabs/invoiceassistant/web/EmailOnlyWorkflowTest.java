package com.mokapotlabs.invoiceassistant.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mokapotlabs.invoiceassistant.app.ConfirmationResult;
import com.mokapotlabs.invoiceassistant.app.InvoiceAssistantFacade;
import com.mokapotlabs.invoiceassistant.readside.InvoiceHistoryQuery;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@QuarkusTest
@ResourceLock("invoice-assistant-data-root")
class EmailOnlyWorkflowTest {
    @Inject
    InvoiceAssistantFacade facade;

    @Inject
    InvoiceHistoryQuery history;

    @Inject
    com.mokapotlabs.invoiceassistant.analysis.InvoiceExecutionTracker executions;

    @Test
    void manualConfirmationSendsAndPersistsOnlyEmailOutcome() throws Exception {
        var session = PayloadUploadFixture.session();
        String location = given().cookie("invoice-local-session", session)
                .contentType("application/json")
                .body(PayloadUploadFixture.uploads(PayloadUploadFixture.file(
                        "manual-" + UUID.randomUUID() + ".pdf", InvoiceAutoApprovalTest.pdf(), "application/pdf")))
                .redirects()
                .follow(false)
                .post("/invoices")
                .then()
                .statusCode(303)
                .extract()
                .header("Location");
        UUID id = UUID.fromString(URI.create(location).getPath().split("/")[2]);
        waitFor(() -> facade.review(id)
                .filter(r -> r.reviewState().review().isPresent())
                .isPresent());
        var review = facade.review(id).orElseThrow().reviewState().review().orElseThrow();
        assertTrue(facade.review(id).orElseThrow().reviewState().confirmation().isEmpty());
        assertInstanceOf(
                ConfirmationResult.Accepted.class,
                facade.confirmProperty(
                        id,
                        "maple-street",
                        review.facts().supplier(),
                        review.note(),
                        "accountant@example.test",
                        "Fixture email",
                        "PDF attached"));
        waitFor(() -> history.history(id).delivery().isPresent()
                && facade.review(id)
                                .orElseThrow()
                                .deliveryState()
                                .notification()
                                .status()
                        == com.mokapotlabs.invoiceassistant.storage.DeliveryOperationStatus.SUCCEEDED);
        var audit = history.history(id);
        assertTrue(audit.confirmation().isPresent());
        assertEquals(1, audit.notificationOutcomes().size());
        assertEquals("SUCCEEDED", audit.delivery().orElseThrow().notificationTruth);
        given().cookie("invoice-local-session", session)
                .get("/invoices/" + id + "/delivery")
                .then()
                .statusCode(200)
                .body(containsString("Accountant delivery"))
                .body(containsString("E-mail sent"))
                .body(containsString("alert alert--success"))
                .body(containsString("<details class=\"delivery-reissue\">"))
                .body(containsString("Need to send this again?"))
                .body(not(containsString("Fake accountant delivery accepted the PDF attachment.")));
        // Lose the disposable projection, retaining native Command authority.
        waitFor(() -> executions.claimSuccessfulDeliveryReissue(id).isPresent());
        given().cookie("invoice-local-session", session)
                .get("/fragments/invoices/" + id + "/delivery-status")
                .then()
                .statusCode(200)
                .body(containsString("Send invoice again"))
                .body(not(containsString("Automatic resend is unavailable")));

        given().cookie("invoice-local-session", session)
                .redirects()
                .follow(false)
                .formParam("reason", "Accountant requested another copy")
                .post("/invoices/" + id + "/delivery/reissue")
                .then()
                .statusCode(303);
        waitFor(() -> history.history(id).notificationOutcomes().size() == 2
                && executions.state(id).isEmpty());
    }

    @Test
    void outlookStateRequiresSessionAndExposesNoTokens() {
        given().get("/outlook/status").then().statusCode(401);
        given().post("/outlook/connect").then().statusCode(401);
        String session =
                given().get("/outlook").then().statusCode(200).extract().cookie("invoice-local-session");
        given().cookie("invoice-local-session", session)
                .get("/outlook/status")
                .then()
                .statusCode(200)
                .body("status", equalTo("UNCONFIGURED"))
                .body(not(containsString("accessToken")));
        given().cookie("invoice-local-session", session)
                .header("Origin", "https://outside.example")
                .post("/outlook/connect")
                .then()
                .statusCode(401);
        given().post("/invoices/" + UUID.randomUUID() + "/delivery/retry")
                .then()
                .statusCode(401);
    }

    private void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
        Instant deadline = Instant.now().plusSeconds(15);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(25);
        }
        fail("Email workflow did not reach the expected boundary");
    }
}
