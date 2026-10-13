package com.mokapotlabs.invoiceassistant.web;

import static io.restassured.RestAssured.given;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Fixtures exercise the generated transfer route and the separate typed admission route. */
final class PayloadUploadFixture {
    record File(String name, byte[] bytes, String mediaType) {}

    static File file(String name, byte[] bytes, String mediaType) {
        return new File(name, bytes, mediaType);
    }

    static String session() {
        return given().redirects().follow(false).get("/").getCookie("invoice-local-session");
    }

    static List<Map<String, Object>> uploads(File... files) {
        String session = session();
        return Arrays.stream(files)
                .map(file -> {
                    Object reference = given().cookie("invoice-local-session", session)
                            .header("X-Tenant-Id", "local")
                            .header("X-Scope-Id", "invoices")
                            .multiPart("file", file.name(), file.bytes(), file.mediaType())
                            .post("/tpf/payloads/invoice-file/upload")
                            .then()
                            .statusCode(200)
                            .extract()
                            .path("invoice");
                    return Map.of("invoice", reference, "originalFilename", (Object) file.name());
                })
                .toList();
    }
}
