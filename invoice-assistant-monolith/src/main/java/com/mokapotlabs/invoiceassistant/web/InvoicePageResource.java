package com.mokapotlabs.invoiceassistant.web;

import com.mokapotlabs.invoiceassistant.app.ConfirmationResult;
import com.mokapotlabs.invoiceassistant.app.DeliveryRetryResult;
import com.mokapotlabs.invoiceassistant.app.InvoiceAssistantFacade;
import com.mokapotlabs.invoiceassistant.app.UploadResult;
import com.mokapotlabs.invoiceassistant.domain.Property;
import com.mokapotlabs.invoiceassistant.readside.InvoiceHistory;
import com.mokapotlabs.invoiceassistant.readside.InvoiceHistoryQuery;
import com.mokapotlabs.invoiceassistant.readside.InvoiceReadModel;
import com.mokapotlabs.invoiceassistant.settings.SettingsRepository;
import com.mokapotlabs.invoiceassistant.storage.InvoiceInbox;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;

@Path("/")
public class InvoicePageResource {
    private static final System.Logger LOG = System.getLogger(InvoicePageResource.class.getName());

    @Inject
    InvoiceAssistantFacade facade;

    @Inject
    SettingsRepository settingsRepository;

    @Inject
    InvoiceHistoryQuery historyQuery;

    @Inject
    InvoiceReviewQueue reviewQueue;

    @Inject
    InvoiceEmailDraftFactory emailDrafts;

    @ConfigProperty(name = "invoice-assistant.max-upload-bytes")
    long maxUploadBytes;

    @ConfigProperty(name = "invoice-assistant.max-upload-files")
    int maxUploadFiles;

    @ConfigProperty(name = "invoice-assistant.max-upload-batch-bytes")
    long maxUploadBatchBytes;

    @CheckedTemplate(basePath = "")
    static class Templates {
        static native TemplateInstance inbox(InvoiceInbox inbox, InvoiceUploadLimits limits, boolean autoApprove);

        static native TemplateInstance upload(
                InvoiceUploadLimits limits, String message, String duplicateUrl, boolean autoApprove);

        static native TemplateInstance uploadBatch(InvoiceBatchUploadView batch, InvoiceUploadLimits limits);

        static native TemplateInstance review(
                InvoiceReadModel record,
                String message,
                List<Property> properties,
                ReviewQueueNavigation navigation,
                InvoiceEmailDraft email);

        static native TemplateInstance reviewPending(UUID documentId);

        static native TemplateInstance reviewQueue(InvoiceReviewQueueView queue);

        static native TemplateInstance delivery(
                InvoiceReadModel record, String message, boolean confirmationPending, boolean resendAvailable);
    }

    @CheckedTemplate(basePath = "fragments")
    static class Fragments {
        static native TemplateInstance message(String message, String duplicateUrl);

        static native TemplateInstance reviewStatus(
                InvoiceReadModel record,
                String message,
                List<Property> properties,
                ReviewQueueNavigation navigation,
                InvoiceEmailDraft email);

        static native TemplateInstance reviewQueue(InvoiceReviewQueueView queue);

        static native TemplateInstance deliveryStatus(
                InvoiceReadModel record, String message, boolean confirmationPending, boolean resendAvailable);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public Response inbox() {
        if (!settingsRepository.initializeAndLoad().settings().isReady()) {
            return Response.seeOther(URI.create("/setup")).build();
        }
        return Response.ok(Templates.inbox(
                        facade.inbox(),
                        uploadLimits(),
                        settingsRepository.load().settings().autoApprove()))
                .build();
    }

    @GET
    @Path("invoices/new")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance uploadForm() {
        return Templates.upload(
                uploadLimits(), "", "", settingsRepository.load().settings().autoApprove());
    }

    @POST
    @io.quarkus.security.Authenticated
    @Path("invoices")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public Response admit(
            List<com.mokapotlabs.invoiceassistant.app.UploadedInvoice> files,
            @jakarta.ws.rs.core.Context jakarta.ws.rs.core.SecurityContext identity,
            @HeaderParam("HX-Request") String htmxRequest) {
        var selected = List.copyOf(files);
        var rejection = uploadLimits()
                .validateBatch(selected.stream()
                        .map(file -> file.invoice().sizeBytes())
                        .toList());
        if (rejection.isPresent()) return rejected(rejection.orElseThrow(), "", htmxRequest);
        List<InvoiceBatchUploadView.Item> items = new ArrayList<>();
        List<UUID> accepted = new ArrayList<>();
        for (var file : selected) {
            UploadResult result =
                    facade.admitInvoice(file, identity.getUserPrincipal().getName());
            if (selected.size() == 1) return uploadResult(result, htmxRequest);
            var item = InvoiceBatchUploadView.Item.from(file.originalFilename(), result);
            items.add(item);
            if (item.accepted()) accepted.add(item.documentId().orElseThrow());
        }
        var queue = accepted.isEmpty()
                ? Optional.<ReviewQueueToken>empty()
                : Optional.of(ReviewQueueToken.of(accepted, maxUploadFiles));
        return Response.ok(Templates.uploadBatch(new InvoiceBatchUploadView(items, queue), uploadLimits()))
                .build();
    }

    private Response uploadResult(UploadResult result, String htmxRequest) {
        if (result instanceof UploadResult.Accepted) {
            String reviewPath =
                    "/invoices/" + ((UploadResult.Accepted) result).document().documentId() + "/review";
            return redirect(reviewPath, htmxRequest);
        }
        if (result instanceof UploadResult.Duplicate duplicate) {
            return rejected(
                    "This PDF was already uploaded.",
                    "/invoices/" + duplicate.existingDocument().documentId() + "/review",
                    htmxRequest);
        }
        if (result instanceof UploadResult.Rejected rejected) {
            return rejected(rejected.message(), "", htmxRequest);
        }
        return rejected(((UploadResult.Failed) result).message(), "", htmxRequest);
    }

    @GET
    @Path("invoices/{documentId}/review")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public Response review(@PathParam("documentId") UUID documentId, @QueryParam("queue") String queue) {
        ReviewQueueNavigation navigation;
        try {
            navigation = reviewNavigation(queue, documentId);
        } catch (IllegalArgumentException exception) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(exception.getMessage())
                    .build();
        }
        return facade.review(documentId)
                .<Response>map(record -> Response.ok(
                                Templates.review(record, "", facade.properties(), navigation, emailDraft(record)))
                        .build())
                .orElseGet(
                        () -> Response.ok(Templates.reviewPending(documentId)).build());
    }

    @GET
    @Path("invoices/review-queue")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public Response reviewQueue(@QueryParam("queue") String encodedQueue) {
        try {
            InvoiceReviewQueueView queue = reviewQueue.view(ReviewQueueToken.parse(encodedQueue, maxUploadFiles));
            if (queue.nextReady().isPresent()) {
                return Response.seeOther(URI.create(queue.nextReviewUrl())).build();
            }
            return Response.ok(Templates.reviewQueue(queue)).build();
        } catch (IllegalArgumentException exception) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(exception.getMessage())
                    .build();
        }
    }

    @GET
    @Path("fragments/invoices/review-queue")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public Response reviewQueueStatus(@QueryParam("queue") String encodedQueue) {
        try {
            InvoiceReviewQueueView queue = reviewQueue.view(ReviewQueueToken.parse(encodedQueue, maxUploadFiles));
            if (queue.nextReady().isPresent()) {
                return Response.noContent()
                        .header("HX-Redirect", queue.nextReviewUrl())
                        .build();
            }
            return Response.ok(Fragments.reviewQueue(queue)).build();
        } catch (IllegalArgumentException exception) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(exception.getMessage())
                    .build();
        }
    }

    @GET
    @Path("invoices/{documentId}/history")
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public InvoiceHistory history(@PathParam("documentId") UUID documentId) {
        return historyQuery.history(documentId);
    }

    @GET
    @Path("fragments/invoices/{documentId}/review-status")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public Response reviewStatus(@PathParam("documentId") UUID documentId) {
        return facade.review(documentId)
                .<Response>map(record -> Response.ok(Fragments.reviewStatus(
                                record, "", facade.properties(), ReviewQueueNavigation.inactive(), emailDraft(record)))
                        .build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("fragments/invoices/{documentId}/delivery-status")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public Response deliveryStatus(
            @PathParam("documentId") UUID documentId, @QueryParam("confirmationPending") boolean confirmationPending) {
        return facade.review(documentId)
                .<Response>map(record -> Response.ok(
                                Fragments.deliveryStatus(record, "", confirmationPending, resendAvailable(record)))
                        .build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @io.quarkus.security.Authenticated
    @Path("invoices/{documentId}/property")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public Response confirmProperty(
            @PathParam("documentId") UUID documentId,
            @RestForm("propertyId") String propertyId,
            @RestForm("supplier") String supplier,
            @RestForm("note") String note,
            @RestForm("emailRecipient") String emailRecipient,
            @RestForm("emailSubject") String emailSubject,
            @RestForm("emailBody") String emailBody,
            @RestForm("queue") String encodedQueue,
            @HeaderParam("HX-Request") String htmxRequest) {
        Optional<ReviewQueueToken> queue;
        try {
            queue = optionalReviewQueue(encodedQueue);
            if (queue.isPresent() && !queue.orElseThrow().contains(documentId)) {
                throw new IllegalArgumentException("current invoice is not part of the review queue");
            }
        } catch (IllegalArgumentException exception) {
            return invalidConfirmation(
                    documentId,
                    exception.getMessage(),
                    ReviewQueueNavigation.inactive(),
                    new InvoiceEmailDraft(emailRecipient, emailSubject, emailBody));
        }
        ConfirmationResult result =
                facade.confirmProperty(documentId, propertyId, supplier, note, emailRecipient, emailSubject, emailBody);
        if (result instanceof ConfirmationResult.Accepted) {
            if (queue.isPresent()) {
                return redirect(
                        "/invoices/review-queue?queue=" + queue.orElseThrow().encoded(), htmxRequest);
            }
            return redirect("/invoices/" + documentId + "/delivery?confirmationPending=true", htmxRequest);
        }
        ReviewQueueNavigation navigation = queue.map(value -> ReviewQueueNavigation.active(value, documentId))
                .orElseGet(ReviewQueueNavigation::inactive);
        return facade.review(documentId)
                .<Response>map(record -> Response.status(Response.Status.BAD_REQUEST)
                        .entity(Templates.review(
                                record,
                                ((ConfirmationResult.Rejected) result).message(),
                                facade.properties(),
                                navigation,
                                new InvoiceEmailDraft(emailRecipient, emailSubject, emailBody)))
                        .build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("invoices/{documentId}/delivery")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public Response delivery(
            @PathParam("documentId") UUID documentId, @QueryParam("confirmationPending") boolean confirmationPending) {
        return facade.review(documentId)
                .<Response>map(record -> Response.ok(
                                Templates.delivery(record, "", confirmationPending, resendAvailable(record)))
                        .build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @io.quarkus.security.Authenticated
    @Path("invoices/{documentId}/analysis/redrive")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public Response redriveAnalysis(
            @PathParam("documentId") UUID documentId, @HeaderParam("HX-Request") String htmxRequest) {
        DeliveryRetryResult result = facade.redriveAnalysis(documentId);
        if (result instanceof DeliveryRetryResult.Accepted) {
            if (!isHtmx(htmxRequest)) {
                return Response.seeOther(URI.create("/invoices/" + documentId + "/review"))
                        .build();
            }
            return facade.review(documentId)
                    .<Response>map(record -> Response.ok(Fragments.reviewStatus(
                                    record,
                                    "",
                                    facade.properties(),
                                    ReviewQueueNavigation.inactive(),
                                    emailDraft(record)))
                            .build())
                    .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
        }
        String message = ((DeliveryRetryResult.Rejected) result).message();
        return facade.review(documentId)
                .<Response>map(record -> isHtmx(htmxRequest)
                        ? Response.ok()
                                .entity(Fragments.reviewStatus(
                                        record,
                                        message,
                                        facade.properties(),
                                        ReviewQueueNavigation.inactive(),
                                        emailDraft(record)))
                                .build()
                        : Response.status(Response.Status.CONFLICT)
                                .entity(Templates.review(
                                        record,
                                        message,
                                        facade.properties(),
                                        ReviewQueueNavigation.inactive(),
                                        emailDraft(record)))
                                .build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @io.quarkus.security.Authenticated
    @Path("invoices/{documentId}/delivery/retry")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public Response retryDelivery(
            @PathParam("documentId") UUID documentId,
            @RestForm("acknowledgePossibleDuplicate") boolean acknowledgePossibleDuplicate,
            @HeaderParam("HX-Request") String htmxRequest) {
        DeliveryRetryResult result = facade.retryDelivery(documentId, acknowledgePossibleDuplicate);
        if (result instanceof DeliveryRetryResult.Accepted) {
            if (!isHtmx(htmxRequest)) {
                return Response.seeOther(URI.create("/invoices/" + documentId + "/delivery"))
                        .build();
            }
            return facade.review(documentId)
                    .<Response>map(
                            record -> Response.ok(Fragments.deliveryStatus(record, "", false, resendAvailable(record)))
                                    .build())
                    .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
        }
        String message = ((DeliveryRetryResult.Rejected) result).message();
        return facade.review(documentId)
                .<Response>map(record -> isHtmx(htmxRequest)
                        ? Response.ok()
                                .entity(Fragments.deliveryStatus(record, message, false, resendAvailable(record)))
                                .build()
                        : Response.status(Response.Status.CONFLICT)
                                .entity(Templates.delivery(record, message, false, resendAvailable(record)))
                                .build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @io.quarkus.security.Authenticated
    @Path("invoices/{documentId}/delivery/reissue")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public Response reissueDelivery(
            @PathParam("documentId") UUID documentId,
            @RestForm("reason") String reason,
            @RestForm("acknowledgePossibleDuplicate") boolean acknowledgePossibleDuplicate,
            @HeaderParam("HX-Request") String htmxRequest) {
        if (facade.review(documentId)
                        .map(record -> record.deliveryState().notification().status()
                                == com.mokapotlabs.invoiceassistant.storage.DeliveryOperationStatus
                                        .SUBMISSION_UNCERTAIN)
                        .orElse(false)
                && !acknowledgePossibleDuplicate)
            return deliveryConflict(
                    documentId,
                    "Check Outlook Sent Items and acknowledge the possible duplicate before sending again.",
                    htmxRequest);
        DeliveryRetryResult result = facade.reissueNotification(documentId, reason);
        if (result instanceof DeliveryRetryResult.Accepted) {
            if (!isHtmx(htmxRequest)) {
                return Response.seeOther(URI.create("/invoices/" + documentId + "/delivery"))
                        .build();
            }
            return facade.review(documentId)
                    .<Response>map(
                            record -> Response.ok(Fragments.deliveryStatus(record, "", false, resendAvailable(record)))
                                    .build())
                    .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
        }
        String message = ((DeliveryRetryResult.Rejected) result).message();
        return deliveryConflict(documentId, message, htmxRequest);
    }

    private boolean resendAvailable(InvoiceReadModel record) {
        var status = record.deliveryState().notification().status();
        return record.workflowFailure().isEmpty()
                && (status == com.mokapotlabs.invoiceassistant.storage.DeliveryOperationStatus.SUCCEEDED
                        || status
                                == com.mokapotlabs.invoiceassistant.storage.DeliveryOperationStatus
                                        .SUBMISSION_UNCERTAIN)
                && facade.canReissueNotification(record.document().documentId());
    }

    private Response deliveryConflict(UUID documentId, String message, String htmxRequest) {
        return facade.review(documentId)
                .<Response>map(record -> isHtmx(htmxRequest)
                        ? Response.status(Response.Status.CONFLICT)
                                .entity(Fragments.deliveryStatus(record, message, false, resendAvailable(record)))
                                .build()
                        : Response.status(Response.Status.CONFLICT)
                                .entity(Templates.delivery(record, message, false, resendAvailable(record)))
                                .build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @io.quarkus.security.Authenticated
    @Path("invoices/{documentId}/dismiss")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public Response dismissFromInbox(
            @PathParam("documentId") UUID documentId, @HeaderParam("HX-Request") String htmxRequest) {
        if (facade.review(documentId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        facade.dismissFromInbox(documentId);
        if (isHtmx(htmxRequest)) {
            return Response.ok("").build();
        }
        return Response.seeOther(URI.create("/")).build();
    }

    private Response rejected(String message, String duplicateUrl, String htmxRequest) {
        if (isHtmx(htmxRequest)) {
            return Response.ok(Fragments.message(message, duplicateUrl)).build();
        }
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Templates.upload(
                        uploadLimits(),
                        message,
                        duplicateUrl,
                        settingsRepository.load().settings().autoApprove()))
                .build();
    }

    private Response invalidConfirmation(
            UUID documentId, String message, ReviewQueueNavigation navigation, InvoiceEmailDraft email) {
        return facade.review(documentId)
                .<Response>map(record -> Response.status(Response.Status.BAD_REQUEST)
                        .entity(Templates.review(record, message, facade.properties(), navigation, email))
                        .build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    private ReviewQueueNavigation reviewNavigation(String encodedQueue, UUID documentId) {
        return optionalReviewQueue(encodedQueue)
                .map(queue -> ReviewQueueNavigation.active(queue, documentId))
                .orElseGet(ReviewQueueNavigation::inactive);
    }

    private Optional<ReviewQueueToken> optionalReviewQueue(String encodedQueue) {
        if (encodedQueue == null || encodedQueue.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(ReviewQueueToken.parse(encodedQueue, maxUploadFiles));
    }

    private InvoiceUploadLimits uploadLimits() {
        return new InvoiceUploadLimits(maxUploadFiles, maxUploadBytes, maxUploadBatchBytes);
    }

    private InvoiceEmailDraft emailDraft(InvoiceReadModel record) {
        return record.reviewState().review().isPresent() ? emailDrafts.create(record) : InvoiceEmailDraft.empty();
    }

    private Response redirect(String path, String htmxRequest) {
        if (isHtmx(htmxRequest)) {
            // Do not let HTMX swap an empty success body before navigating.
            return Response.noContent().header("HX-Redirect", path).build();
        }
        return Response.seeOther(URI.create(path)).build();
    }

    private static boolean isHtmx(String requestHeader) {
        return "true".equalsIgnoreCase(requestHeader);
    }
}
