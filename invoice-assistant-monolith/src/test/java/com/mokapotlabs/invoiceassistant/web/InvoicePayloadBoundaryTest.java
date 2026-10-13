package com.mokapotlabs.invoiceassistant.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@QuarkusTest
@ResourceLock("invoice-assistant-data-root")
class InvoicePayloadBoundaryTest {
    @Test
    void requiresSessionAndRejectsWrongOwner() {
        given().multiPart("file", "invoice.pdf", "%PDF-test".getBytes(), "application/pdf")
                .post("/tpf/payloads/invoice-file/upload")
                .then()
                .statusCode(401);
        String session = session();
        given().cookie("invoice-local-session", session)
                .header("X-Tenant-Id", "other")
                .header("X-Scope-Id", "invoices")
                .multiPart("file", "invoice.pdf", "%PDF-test".getBytes(), "application/pdf")
                .post("/tpf/payloads/invoice-file/upload")
                .then()
                .statusCode(403);
    }

    @Test
    void generatesOwnedReferenceAndDownloadsExactBytes() throws Exception {
        String session = session();
        byte[] pdf = InvoiceAutoApprovalTest.pdf();
        Map<String, Object> reference = given().cookie("invoice-local-session", session)
                .header("X-Tenant-Id", "local")
                .header("X-Scope-Id", "invoices")
                .multiPart("file", "native.pdf", pdf, "application/pdf")
                .post("/tpf/payloads/invoice-file/upload")
                .then()
                .statusCode(200)
                .body("invoice.metadata.'tpf.payload.owner.tenant'", equalTo("local"))
                .extract()
                .path("invoice");
        byte[] downloaded = given().cookie("invoice-local-session", session)
                .header("X-Tenant-Id", "local")
                .header("X-Scope-Id", "invoices")
                .contentType("application/json")
                .body(reference)
                .post("/tpf/payloads/invoice-file-download/download")
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
        org.junit.jupiter.api.Assertions.assertArrayEquals(pdf, downloaded);
        given().cookie("invoice-local-session", session)
                .contentType("application/json")
                .body(List.of(Map.of("invoice", reference, "originalFilename", "native.txt")))
                .redirects()
                .follow(false)
                .post("/invoices")
                .then()
                .statusCode(400)
                .body(containsString("Only PDF files can be uploaded."));
    }

    private static String session() {
        return given().redirects().follow(false).get("/").getCookie("invoice-local-session");
    }
}
