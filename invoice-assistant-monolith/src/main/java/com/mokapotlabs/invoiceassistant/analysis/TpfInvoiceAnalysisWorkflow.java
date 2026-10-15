package com.mokapotlabs.invoiceassistant.analysis;

import com.mokapotlabs.invoiceassistant.app.DeliveryRetryResult;
import com.mokapotlabs.invoiceassistant.cache.DeliveryEffectIdentity;
import com.mokapotlabs.invoiceassistant.domain.InvoiceFiles;
import com.mokapotlabs.invoiceassistant.domain.NotifyAccountantCommand;
import com.mokapotlabs.invoiceassistant.domain.NotifyAttempt;
import com.mokapotlabs.invoiceassistant.domain.PayloadReferenceIdentity;
import com.mokapotlabs.invoiceassistant.domain.ReviewReady;
import com.mokapotlabs.invoiceassistant.interaction.PropertyConfirmationRequest;
import com.mokapotlabs.invoiceassistant.interaction.SubmittedConfirmation;
import com.mokapotlabs.invoiceassistant.readside.InvoiceAdmissionQuery;
import com.mokapotlabs.invoiceassistant.readside.InvoiceReadModelQuery;
import com.mokapotlabs.invoiceassistant.readside.InvoiceRecoveryQuery;
import com.mokapotlabs.invoiceassistant.readside.InvoiceReviewQuery;
import com.mokapotlabs.invoiceassistant.storage.DeliveryOperationStatus;
import com.mokapotlabs.invoiceassistant.storage.InvoiceWorkflowFailure;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.pipelineframework.PipelineExecutionService;
import org.pipelineframework.awaitable.AwaitCompletionCommand;
import org.pipelineframework.awaitable.AwaitInteractionStatus;
import org.pipelineframework.awaitable.AwaitPayloadSupport;
import org.pipelineframework.command.CommandEffectStatus;
import org.pipelineframework.command.CommandEffectStore;
import org.pipelineframework.orchestrator.ExecutionRedriveIntent;
import org.pipelineframework.orchestrator.ExecutionStatus;
import org.pipelineframework.orchestrator.PipelineControlPlane;
import org.pipelineframework.orchestrator.dto.ExecutionStatusDto;
import org.pipelineframework.orchestrator.dto.RunAsyncAcceptedDto;

/** The only application adapter that submits or resumes the generated TPF workflow. */
@ApplicationScoped
public class TpfInvoiceAnalysisWorkflow {
    private static final System.Logger LOG = System.getLogger(TpfInvoiceAnalysisWorkflow.class.getName());
    private static final String TENANT_ID = "local-invoice-assistant";
    private static final Duration STATUS_POLL_INTERVAL = Duration.ofMillis(250);
    private static final Duration STATUS_READ_TIMEOUT = Duration.ofSeconds(10);
    private final PipelineExecutionService executionService;
    private final PipelineControlPlane controlPlane;
    private final InvoiceExecutionTracker executions;
    private final InvoiceAdmissionQuery admissions;
    private final InvoiceReviewQuery reviews;
    private final InvoiceRecoveryQuery recovery;
    private final InvoiceReadModelQuery history;
    private final CommandEffectStore effects;

    public TpfInvoiceAnalysisWorkflow(
            PipelineExecutionService executionService,
            PipelineControlPlane controlPlane,
            InvoiceExecutionTracker executions,
            InvoiceAdmissionQuery admissions,
            InvoiceReviewQuery reviews,
            InvoiceRecoveryQuery recovery,
            InvoiceReadModelQuery history,
            CommandEffectStore effects) {
        this.executionService = executionService;
        this.controlPlane = controlPlane;
        this.executions = executions;
        this.admissions = admissions;
        this.reviews = reviews;
        this.recovery = recovery;
        this.history = history;
        this.effects = effects;
    }

    public void process(InvoiceFiles files) {
        startAnalysis(files.documentId(), files, "process");
    }

    private void startAnalysis(UUID documentId, Object request, String operation) {
        executions.analysisRunning(documentId);
        try {
            RunAsyncAcceptedDto accepted = submit(request, operation + ":" + documentId);
            Infrastructure.getDefaultWorkerPool()
                    .execute(() -> observe(documentId, accepted.executionId(), WorkflowPhase.ANALYSIS));
        } catch (RuntimeException failure) {
            recordFailure(documentId, WorkflowPhase.ANALYSIS, failure);
        }
    }

    public boolean completeConfirmation(
            UUID documentId,
            String propertyId,
            String supplier,
            String note,
            String emailRecipient,
            String emailSubject,
            String emailBody) {
        return completeConfirmation(
                documentId,
                propertyId,
                supplier,
                note,
                emailRecipient,
                emailSubject,
                emailBody,
                true,
                Optional.empty());
    }

    private boolean completeConfirmation(
            UUID documentId,
            String propertyId,
            String supplier,
            String note,
            String emailRecipient,
            String emailSubject,
            String emailBody,
            boolean allowRecovery,
            Optional<String> expectedExecutionId) {
        String confirmedNote = note == null ? "" : note.trim();
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            var interaction =
                    executionService
                            .queryPendingAwaitInteractions(TENANT_ID, "", "", "", 100)
                            .await()
                            .indefinitely()
                            .stream()
                            // A locally observed interaction can still be moving from WAITING to
                            // DISPATCHED when the operator confirms it. The completion API
                            // correlates it atomically, so accept any non-terminal interaction
                            // belonging to this invoice rather than turning that short handoff
                            // race into a user-visible rejection.
                            .filter(candidate -> !candidate.status().terminal())
                            .filter(candidate -> expectedExecutionId
                                    .map(id -> id.equals(candidate.executionId()))
                                    .orElse(true))
                            .filter(candidate -> pendingReview(candidate.requestPayload())
                                    .map(review -> review.documentId().equals(documentId))
                                    .orElse(false))
                            .findFirst();
            if (interaction.isPresent()) {
                var pending = interaction.orElseThrow();
                var review = pendingReview(pending.requestPayload()).orElseThrow();
                // Queue Async marks the interaction dispatched before its LOCAL durable contract is
                // fully visible to a concurrent completion caller. This bounded attended handoff
                // keeps that admission race inside the application boundary.
                pauseForAwaitContractAdmission();
                try {
                    var completion = executionService
                            .completeAwaitInteraction(new AwaitCompletionCommand(
                                    TENANT_ID,
                                    pending.interactionId(),
                                    pending.correlationId(),
                                    "confirm:" + documentId + ":" + propertyId + ":"
                                            + PayloadReferenceIdentity.sha256(supplier.trim() + "\n" + confirmedNote
                                                    + "\n" + emailRecipient.trim() + "\n" + emailSubject.trim()
                                                    + "\n" + emailBody.trim()),
                                    new SubmittedConfirmation(
                                            propertyId,
                                            supplier,
                                            confirmedNote,
                                            emailRecipient,
                                            emailSubject,
                                            emailBody),
                                    "local-user",
                                    Instant.now().toEpochMilli()))
                            .await()
                            .indefinitely();
                    if (completion.record().status() != AwaitInteractionStatus.COMPLETED
                            && completion.record().status() != AwaitInteractionStatus.COMPLETION_OBSERVED) {
                        throw new IllegalStateException("Confirmation was not admitted: "
                                + completion.record().status());
                    }
                    executions.deliveryRunning(documentId, DeliveryEffectIdentity.notification(review.reviewId()));
                    Infrastructure.getDefaultWorkerPool()
                            .execute(() -> observe(documentId, pending.executionId(), WorkflowPhase.DELIVERY));
                    return true;
                } catch (RuntimeException failure) {
                    // Completion can be durably accepted before a continuation handoff fails.
                    if (expectedExecutionId.isEmpty()
                            && (history.find(documentId)
                                            .flatMap(record ->
                                                    record.reviewState().confirmation())
                                            .isPresent()
                                    || controlPlane
                                            .getAwaitCompletionObservation(TENANT_ID, pending.interactionId())
                                            .await()
                                            .indefinitely()
                                            .admission()
                                            .isPresent())) {
                        executions.deliveryRunning(documentId, DeliveryEffectIdentity.notification(review.reviewId()));
                        Infrastructure.getDefaultWorkerPool()
                                .execute(() -> observe(documentId, pending.executionId(), WorkflowPhase.DELIVERY));
                        return true;
                    }
                    throw new IllegalArgumentException(
                            InvoiceWorkflowFailure.safeDetail(
                                    failure, "The confirmation could not be accepted. Try again."),
                            failure);
                }
            }
            pauseBeforeInteractionRetry();
        }
        if (expectedExecutionId.isEmpty()
                && history.find(documentId)
                        .flatMap(record -> record.reviewState().confirmation())
                        .isPresent()) return true;
        if (allowRecovery && resumeReview(documentId) instanceof DeliveryRetryResult.Accepted) {
            return completeConfirmation(
                    documentId,
                    propertyId,
                    supplier,
                    confirmedNote,
                    emailRecipient,
                    emailSubject,
                    emailBody,
                    false,
                    Optional.empty());
        }
        return false;
    }

    public DeliveryRetryResult resumeReview(UUID documentId) {
        try {
            if (history.find(documentId)
                    .flatMap(record -> record.reviewState().confirmation())
                    .isPresent()) {
                return new DeliveryRetryResult.Rejected("This invoice is already confirmed. Continue to delivery.");
            }
            boolean pending =
                    executionService
                            .queryPendingAwaitInteractions(TENANT_ID, "", "", "", 100)
                            .await()
                            .indefinitely()
                            .stream()
                            .anyMatch(candidate -> pendingReview(candidate.requestPayload())
                                    .map(review -> review.documentId().equals(documentId))
                                    .orElse(false));
            if (pending) return new DeliveryRetryResult.Accepted();
            var review = recovery.review(documentId);
            if (review.isEmpty()) return new DeliveryRetryResult.Rejected("No saved invoice review is available.");
            if (!executions.claimAnalysisRestart(documentId)) {
                return new DeliveryRetryResult.Rejected("Invoice processing is already running.");
            }
            RunAsyncAcceptedDto accepted = submit(review.orElseThrow(), "review-resume:" + documentId);
            Infrastructure.getDefaultWorkerPool()
                    .execute(() -> observe(documentId, accepted.executionId(), WorkflowPhase.ANALYSIS));
            return new DeliveryRetryResult.Accepted();
        } catch (RuntimeException failure) {
            recordFailure(documentId, WorkflowPhase.ANALYSIS, failure);
            return new DeliveryRetryResult.Rejected(
                    InvoiceWorkflowFailure.safeDetail(failure, "Review could not be resumed."));
        }
    }

    public DeliveryRetryResult retryDelivery(UUID documentId) {
        return retryDelivery(documentId, false);
    }

    public DeliveryRetryResult retryDelivery(UUID documentId, boolean acknowledgePossibleDuplicate) {
        var target = executions.failedDeliveryRetry(documentId);
        if (target.isEmpty()) {
            return recoverDelivery(documentId, acknowledgePossibleDuplicate);
        }
        var retry = target.orElseThrow();
        try {
            controlPlane
                    .redriveExecution(
                            TENANT_ID,
                            retry.executionId(),
                            retry.executionVersion(),
                            true,
                            ExecutionRedriveIntent.RETRY_FAILED_COMMAND,
                            "Invoice delivery retry requested by the user")
                    .await()
                    .indefinitely();
            executions.deliveryRunning(documentId);
            Infrastructure.getDefaultWorkerPool()
                    .execute(() -> observe(documentId, retry.executionId(), WorkflowPhase.DELIVERY));
            return new DeliveryRetryResult.Accepted();
        } catch (RuntimeException failure) {
            String detail = InvoiceWorkflowFailure.safeDetail(failure, "The delivery retry could not be started.");
            return new DeliveryRetryResult.Rejected(detail);
        }
    }

    private DeliveryRetryResult recoverDelivery(UUID documentId, boolean acknowledgePossibleDuplicate) {
        var saved = history.find(documentId);
        if (saved.isEmpty() || saved.orElseThrow().reviewState().confirmation().isEmpty()) {
            return new DeliveryRetryResult.Rejected("Confirm this invoice before starting delivery.");
        }
        var record = saved.orElseThrow();
        var notification = record.deliveryState().notification().status();
        if (notification == DeliveryOperationStatus.SUCCEEDED
                || notification == DeliveryOperationStatus.SUBMISSION_UNCERTAIN) {
            return new DeliveryRetryResult.Rejected("This invoice does not have a retryable delivery execution.");
        }
        if (!acknowledgePossibleDuplicate) {
            return new DeliveryRetryResult.Rejected(
                    "The previous execution is unavailable. Check Outlook Sent Items, then acknowledge a possible duplicate before retrying.");
        }
        var approved = record.reviewState().confirmation().orElseThrow();
        String commandId = DeliveryEffectIdentity.notification(approved.reviewId());
        if (!executions.claimDeliveryRecovery(documentId, commandId)) {
            return new DeliveryRetryResult.Rejected(
                    "Invoice delivery is already running or awaiting its retry status.");
        }
        try {
            var input = new NotifyAttempt(new NotifyAccountantCommand(record.document(), approved));
            String pendingCommandId = commandId;
            var effect = effects.find(TENANT_ID, pendingCommandId).await().indefinitely();
            if (effect.isPresent() && effect.orElseThrow().status() == CommandEffectStatus.FAILED_RETRYABLE) {
                var status = controlPlane
                        .getExecutionStatus(TENANT_ID, effect.orElseThrow().executionId())
                        .await()
                        .indefinitely();
                controlPlane
                        .redriveExecution(
                                TENANT_ID,
                                status.executionId(),
                                status.version(),
                                true,
                                ExecutionRedriveIntent.RETRY_FAILED_COMMAND,
                                "User acknowledged recovery of interrupted invoice delivery")
                        .await()
                        .indefinitely();
                Infrastructure.getDefaultWorkerPool()
                        .execute(() -> observe(documentId, status.executionId(), WorkflowPhase.DELIVERY));
            } else {
                RunAsyncAcceptedDto accepted = submit(input, "delivery-recovery:" + documentId);
                Infrastructure.getDefaultWorkerPool()
                        .execute(() -> observe(documentId, accepted.executionId(), WorkflowPhase.DELIVERY));
            }
            return new DeliveryRetryResult.Accepted();
        } catch (RuntimeException failure) {
            recordFailure(documentId, WorkflowPhase.DELIVERY, failure);
            return new DeliveryRetryResult.Rejected(
                    InvoiceWorkflowFailure.safeDetail(failure, "Delivery could not be resumed."));
        }
    }

    public DeliveryRetryResult redriveAnalysis(UUID documentId) {
        if (reviews.latest(documentId).isPresent()) return resumeReview(documentId);
        var target = executions.failedAnalysisRedrive(documentId);
        if (target.isEmpty()) {
            return restartAnalysisFromAdmission(documentId);
        }
        var redrive = target.orElseThrow();
        try {
            controlPlane
                    .redriveExecution(
                            TENANT_ID,
                            redrive.executionId(),
                            redrive.executionVersion(),
                            true,
                            ExecutionRedriveIntent.REPLAY,
                            "Invoice analysis redrive requested by the user")
                    .await()
                    .indefinitely();
            executions.analysisRunning(documentId);
            Infrastructure.getDefaultWorkerPool()
                    .execute(() -> observe(documentId, redrive.executionId(), WorkflowPhase.ANALYSIS));
            return new DeliveryRetryResult.Accepted();
        } catch (RuntimeException failure) {
            String detail = InvoiceWorkflowFailure.safeDetail(failure, "The analysis redrive could not be started.");
            return new DeliveryRetryResult.Rejected(detail);
        }
    }

    private DeliveryRetryResult restartAnalysisFromAdmission(UUID documentId) {
        try {
            if (reviews.latest(documentId).isPresent()) {
                return resumeReview(documentId);
            }
            var files = admissions.find(documentId).map(admissions::files);
            if (files.isEmpty()) {
                return new DeliveryRetryResult.Rejected(
                        "The original invoice admission is unavailable, so analysis cannot be tried again.");
            }
            if (!executions.claimAnalysisRestart(documentId)) {
                return new DeliveryRetryResult.Rejected("Invoice analysis is already running.");
            }
            return restartAnalysis(documentId, files.orElseThrow());
        } catch (RuntimeException failure) {
            return new DeliveryRetryResult.Rejected(
                    InvoiceWorkflowFailure.safeDetail(failure, "The original invoice admission could not be read."));
        }
    }

    private DeliveryRetryResult restartAnalysis(UUID documentId, InvoiceFiles files) {
        try {
            RunAsyncAcceptedDto accepted = submit(files, "analysis-retry:" + documentId);
            Infrastructure.getDefaultWorkerPool()
                    .execute(() -> observe(documentId, accepted.executionId(), WorkflowPhase.ANALYSIS));
            return new DeliveryRetryResult.Accepted();
        } catch (RuntimeException failure) {
            recordFailure(documentId, WorkflowPhase.ANALYSIS, failure);
            return new DeliveryRetryResult.Rejected(
                    InvoiceWorkflowFailure.safeDetail(failure, "The analysis retry could not be started."));
        }
    }

    public DeliveryRetryResult reissueNotification(UUID documentId, String reason) {
        if (reason == null || reason.isBlank()) {
            return new DeliveryRetryResult.Rejected("Enter a reason for sending the invoice again.");
        }
        var target = awaitSuccessfulDelivery(documentId);
        if (target.isEmpty()) {
            return new DeliveryRetryResult.Rejected("This invoice does not have a successful delivery to reissue.");
        }
        var reissue = target.orElseThrow();
        var confirmation =
                history.find(documentId).flatMap(record -> record.reviewState().confirmation());
        if (confirmation.isEmpty()) {
            executions.restoreSuccessfulDeliveryReissue(documentId, reissue);
            return new DeliveryRetryResult.Rejected("The approved invoice message is unavailable.");
        }
        try {
            controlPlane
                    .redriveExecution(
                            TENANT_ID,
                            reissue.executionId(),
                            reissue.executionVersion(),
                            false,
                            ExecutionRedriveIntent.REISSUE_COMMAND,
                            reissue.commandId(),
                            reason.trim())
                    .await()
                    .indefinitely();
            // A native deliberate reissue can open a new Await occurrence. This user's resend
            // request authorizes the already approved message, never another editable review.
            var approved = confirmation.orElseThrow();
            completeConfirmation(
                    documentId,
                    approved.confirmedPropertyId(),
                    approved.supplier(),
                    approved.note(),
                    approved.emailRecipient(),
                    approved.emailSubject(),
                    approved.emailBody(),
                    false,
                    Optional.of(reissue.executionId()));
            executions.deliveryRunning(documentId, reissue.commandId());
            Infrastructure.getDefaultWorkerPool()
                    .execute(() -> observe(documentId, reissue.executionId(), WorkflowPhase.DELIVERY));
            return new DeliveryRetryResult.Accepted();
        } catch (RuntimeException failure) {
            executions.restoreSuccessfulDeliveryReissue(documentId, reissue);
            String detail = InvoiceWorkflowFailure.safeDetail(failure, "The invoice could not be sent again.");
            return new DeliveryRetryResult.Rejected(detail);
        }
    }

    private void pauseBeforeInteractionRetry() {
        pause(Duration.ofMillis(25));
    }

    private Optional<InvoiceExecutionTracker.ReissueTarget> awaitSuccessfulDelivery(UUID documentId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(2));
        Optional<InvoiceExecutionTracker.ReissueTarget> target;
        do {
            target = executions.claimSuccessfulDeliveryReissue(documentId);
            if (target.isPresent()) {
                return target;
            }
            pause(Duration.ofMillis(25));
        } while (Instant.now().isBefore(deadline));
        return Optional.empty();
    }

    private static Optional<ReviewReady> pendingReview(Object payload) {
        try {
            var converted = AwaitPayloadSupport.coercePayload(payload, PropertyConfirmationRequest.class);
            return Optional.of(((PropertyConfirmationRequest) converted).review());
        } catch (RuntimeException exception) {
            System.getLogger(TpfInvoiceAnalysisWorkflow.class.getName())
                    .log(System.Logger.Level.DEBUG, "Await review payload conversion failed", exception);
            return Optional.empty();
        }
    }

    private void pauseForAwaitContractAdmission() {
        pause(Duration.ofMillis(100));
    }

    private void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for property confirmation.", exception);
        }
    }

    private RunAsyncAcceptedDto submit(Object request, String operation) {
        return executionService
                .executePipelineAsync(request, TENANT_ID, operation + ":" + UUID.randomUUID())
                .await()
                .indefinitely();
    }

    private void observe(UUID documentId, String executionId, WorkflowPhase phase) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                ExecutionStatusDto status = executionService
                        .getExecutionStatus(TENANT_ID, executionId)
                        .await()
                        .atMost(STATUS_READ_TIMEOUT);
                if (status.status() == ExecutionStatus.FAILED || status.status() == ExecutionStatus.DLQ) {
                    WorkflowPhase failedPhase = phase == WorkflowPhase.ANALYSIS
                                    && history.find(documentId)
                                            .flatMap(record ->
                                                    record.reviewState().confirmation())
                                            .isPresent()
                            ? WorkflowPhase.DELIVERY
                            : phase;
                    recordFailure(documentId, failedPhase, status, executionId);
                    return;
                }
                if (status.status() == ExecutionStatus.SUCCEEDED
                        || (phase == WorkflowPhase.ANALYSIS && status.status() == ExecutionStatus.WAITING_EXTERNAL)) {
                    WorkflowPhase completedPhase = phase;
                    if (phase == WorkflowPhase.ANALYSIS && status.status() == ExecutionStatus.SUCCEEDED) {
                        var approved = history.find(documentId)
                                .flatMap(record -> record.reviewState().confirmation());
                        if (approved.isPresent()) {
                            completedPhase = WorkflowPhase.DELIVERY;
                            executions.deliveryRunning(
                                    documentId,
                                    DeliveryEffectIdentity.notification(
                                            approved.orElseThrow().reviewId()));
                        }
                    }
                    executions.completed(
                            documentId,
                            completedPhase == WorkflowPhase.ANALYSIS
                                    ? InvoiceExecutionTracker.Phase.ANALYSIS
                                    : InvoiceExecutionTracker.Phase.DELIVERY,
                            executionId,
                            status.version());
                    return;
                }
                pause(STATUS_POLL_INTERVAL);
            } catch (RuntimeException statusReadFailure) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                LOG.log(
                        System.Logger.Level.WARNING,
                        "Could not read analysis execution status for " + executionId + "; polling will continue.",
                        statusReadFailure);
                pause(Duration.ofSeconds(1));
            }
        }
    }

    private void recordFailure(UUID documentId, WorkflowPhase phase, Throwable failure) {
        LOG.log(System.Logger.Level.WARNING, phase.label + " submission failed for " + documentId, failure);
        String detail =
                InvoiceWorkflowFailure.safeDetail(failure, failure.getClass().getSimpleName());
        recordFailure(
                documentId,
                phase,
                new InvoiceWorkflowFailure(phase.label, failure.getClass().getSimpleName(), detail, Instant.now()));
    }

    private void recordFailure(UUID documentId, WorkflowPhase phase, ExecutionStatusDto status, String executionId) {
        String code = InvoiceWorkflowFailure.safeDetail(
                status.errorCode(), status.status().name().toLowerCase());
        String detail = InvoiceWorkflowFailure.safeDetail(status.errorMessage(), "No failure detail was supplied.");
        InvoiceWorkflowFailure failure = new InvoiceWorkflowFailure(phase.label, code, detail, Instant.now());
        executions.failed(
                documentId,
                phase == WorkflowPhase.ANALYSIS
                        ? InvoiceExecutionTracker.Phase.ANALYSIS
                        : InvoiceExecutionTracker.Phase.DELIVERY,
                failure,
                executionId,
                status.version());
    }

    private void recordFailure(UUID documentId, WorkflowPhase phase, InvoiceWorkflowFailure failure) {
        executions.failed(
                documentId,
                phase == WorkflowPhase.ANALYSIS
                        ? InvoiceExecutionTracker.Phase.ANALYSIS
                        : InvoiceExecutionTracker.Phase.DELIVERY,
                failure);
    }

    private enum WorkflowPhase {
        ANALYSIS("Invoice analysis"),
        DELIVERY("Invoice delivery");

        private final String label;

        WorkflowPhase(String label) {
            this.label = label;
        }
    }
}
