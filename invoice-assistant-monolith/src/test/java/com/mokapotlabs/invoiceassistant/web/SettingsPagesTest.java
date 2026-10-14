package com.mokapotlabs.invoiceassistant.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mokapotlabs.invoiceassistant.settings.ApplicationSettings;
import com.mokapotlabs.invoiceassistant.settings.SettingsRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@QuarkusTest
@ResourceLock("invoice-assistant-data-root")
class SettingsPagesTest {
    @Inject
    SettingsRepository settings;

    @Test
    void separateFormsPreserveOtherSettingsAndRememberUploadPreference() throws Exception {
        var previous = settings.initializeAndLoad().settings();
        String session = PayloadUploadFixture.session();
        try {
            given().cookie("invoice-local-session", session)
                    .formParam("enabled", true)
                    .post("/preferences/auto-approval")
                    .then()
                    .statusCode(204);
            given().get("/")
                    .then()
                    .statusCode(200)
                    .body(
                            containsString("data-approval-preference checked"),
                            containsString("Approve if confident"),
                            not(containsString(">Settings</a>")),
                            not(containsString(">Invoices</a>")));
            given().get("/invoices/new")
                    .then()
                    .statusCode(200)
                    .body(containsString("data-approval-preference checked"));
            var configured = settings.load().settings();
            var recipient = given().cookie("invoice-local-session", session)
                    .redirects()
                    .follow(false)
                    .formParam("recipient", "new-accountant@example.test")
                    .formParam("subjectTemplate", "Invoice {supplier}")
                    .formParam("bodyTemplate", "Please see {filename}.")
                    .formParam("recipientPropertyId", ids(configured))
                    .formParam(
                            "accountantRecipientOverride",
                            configured.properties().stream()
                                    .map(p -> p.accountantRecipientOverride())
                                    .toList());
            recipient.post("/recipient").then().statusCode(303).header("Location", endsWith("/recipient?saved=true"));
            var afterRecipient = settings.load().settings();
            assertEquals(configured.properties(), afterRecipient.properties());
            assertEquals(configured.filenameSeparator(), afterRecipient.filenameSeparator());
            assertTrue(afterRecipient.autoApprove());
            assertEquals(
                    "new-accountant@example.test", afterRecipient.accountant().recipient());
            var catalogue = given().cookie("invoice-local-session", session)
                    .redirects()
                    .follow(false)
                    .formParam("filenameSeparator", "-")
                    .formParam("propertyId", ids(afterRecipient))
                    .formParam(
                            "displayName",
                            afterRecipient.properties().stream()
                                    .map(p -> p.displayName())
                                    .toList())
                    .formParam(
                            "canonicalAddress",
                            afterRecipient.properties().stream()
                                    .map(p -> p.canonicalAddress())
                                    .toList())
                    .formParam(
                            "streetNumberPrefix",
                            afterRecipient.properties().stream()
                                    .map(p -> p.streetNumberPrefix())
                                    .toList())
                    .formParam(
                            "enabled",
                            afterRecipient.properties().stream()
                                    .filter(p -> p.enabled())
                                    .map(p -> p.id())
                                    .toList());
            catalogue.post("/catalogue").then().statusCode(303).header("Location", endsWith("/catalogue?saved=true"));
            var afterCatalogue = settings.load().settings();
            assertEquals(afterRecipient.accountant(), afterCatalogue.accountant());
            assertEquals(afterRecipient.properties(), afterCatalogue.properties());
            assertTrue(afterCatalogue.autoApprove());
            assertEquals("-", afterCatalogue.filenameSeparator());
            given().cookie("invoice-local-session", session)
                    .formParam("enabled", false)
                    .post("/preferences/auto-approval")
                    .then()
                    .statusCode(204);
            given().get("/").then().statusCode(200).body(not(containsString("data-approval-preference checked")));
            assertEquals(afterCatalogue.accountant(), settings.load().settings().accountant());
            assertEquals(afterCatalogue.properties(), settings.load().settings().properties());
        } finally {
            settings.save(previous);
        }
    }

    @Test
    void pagesContainOnlyTheirOwnFieldsAndRejectIncompleteOrUnauthenticatedWrites() {
        var previous = settings.initializeAndLoad().settings();
        given().get("/catalogue")
                .then()
                .statusCode(200)
                .body(
                        containsString("Filename separator"),
                        not(containsString("name=\"recipient\"")),
                        not(containsString("Connect Outlook")),
                        not(containsString("autoApprove")));
        given().get("/recipient")
                .then()
                .statusCode(200)
                .body(
                        containsString("Subject template"),
                        not(containsString("Filename separator")),
                        not(containsString("Connect Outlook")));
        given().redirects()
                .follow(false)
                .get("/settings")
                .then()
                .statusCode(303)
                .header("Location", endsWith("/catalogue"));
        given().formParam("enabled", true)
                .post("/preferences/auto-approval")
                .then()
                .statusCode(401);
        given().formParam("filenameSeparator", "_").post("/catalogue").then().statusCode(401);
        given().formParam("recipient", "other@example.test")
                .post("/recipient")
                .then()
                .statusCode(401);
        given().cookie("invoice-local-session", PayloadUploadFixture.session())
                .formParam("filenameSeparator", "-")
                .post("/catalogue")
                .then()
                .statusCode(400);
        assertEquals(previous, settings.load().settings());
    }

    private static List<String> ids(ApplicationSettings value) {
        return value.properties().stream().map(p -> p.id()).toList();
    }
}
