package com.mokapotlabs.invoiceassistant.analysis;

import com.mokapotlabs.invoiceassistant.storage.InvoiceWorkflowFailure;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live projection of executions submitted by this process. Durable business facts remain in TPF
 * persistence; after restart, an admission without a later fact is treated as incomplete.
 */
@ApplicationScoped
public class InvoiceExecutionTracker {
    private static final int MAX_REISSUE_TARGETS = 500;
    private final ConcurrentHashMap<UUID, State> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> notificationCommandIds = new ConcurrentHashMap<>();
    private final Map<UUID, ReissueTarget> successfulDeliveries = new LinkedHashMap<>();

    public synchronized void analysisRunning(UUID documentId) {
        synchronized (successfulDeliveries) {
            successfulDeliveries.remove(documentId);
        }
        notificationCommandIds.remove(documentId);
        states.put(documentId, State.running(Phase.ANALYSIS));
    }

    public synchronized boolean claimAnalysisRestart(UUID documentId) {
        AtomicBoolean claimed = new AtomicBoolean();
        states.compute(documentId, (ignored, current) -> {
            boolean restartable = current == null
                    || (current.phase() == Phase.ANALYSIS
                            && current.failure().isPresent()
                            && current.retryTarget().isEmpty());
            if (!restartable) {
                return current;
            }
            claimed.set(true);
            return State.running(Phase.ANALYSIS);
        });
        if (claimed.get()) {
            synchronized (successfulDeliveries) {
                successfulDeliveries.remove(documentId);
            }
            notificationCommandIds.remove(documentId);
        }
        return claimed.get();
    }

    public synchronized void deliveryRunning(UUID documentId) {
        synchronized (successfulDeliveries) {
            successfulDeliveries.remove(documentId);
        }
        states.put(documentId, State.running(Phase.DELIVERY));
    }

    public synchronized void deliveryRunning(UUID documentId, String notificationCommandId) {
        if (notificationCommandId == null || notificationCommandId.isBlank()) {
            throw new IllegalArgumentException("notificationCommandId must not be blank");
        }
        notificationCommandIds.put(documentId, notificationCommandId);
        deliveryRunning(documentId);
    }

    public synchronized void failed(UUID documentId, Phase phase, InvoiceWorkflowFailure failure) {
        if (phase == Phase.DELIVERY) {
            synchronized (successfulDeliveries) {
                successfulDeliveries.remove(documentId);
            }
        }
        states.put(documentId, State.failed(phase, failure));
    }

    public synchronized void failed(
            UUID documentId, Phase phase, InvoiceWorkflowFailure failure, String executionId, long executionVersion) {
        if (phase == Phase.DELIVERY) {
            synchronized (successfulDeliveries) {
                successfulDeliveries.remove(documentId);
            }
        }
        states.put(documentId, State.failed(phase, failure, new RetryTarget(executionId, executionVersion)));
    }

    public synchronized void completed(UUID documentId, Phase phase, String executionId, long executionVersion) {
        states.remove(documentId);
        if (phase == Phase.DELIVERY) {
            Optional.ofNullable(notificationCommandIds.get(documentId))
                    .ifPresent(commandId -> rememberSuccessfulDelivery(
                            documentId, new ReissueTarget(executionId, executionVersion, commandId)));
            notificationCommandIds.remove(documentId);
        }
    }

    public Optional<State> state(UUID documentId) {
        return Optional.ofNullable(states.get(documentId));
    }

    public Optional<RetryTarget> failedDeliveryRetry(UUID documentId) {
        return state(documentId)
                .filter(state -> state.phase() == Phase.DELIVERY)
                .filter(state -> state.failure().isPresent())
                .flatMap(State::retryTarget);
    }

    public Optional<RetryTarget> failedAnalysisRedrive(UUID documentId) {
        return state(documentId)
                .filter(state -> state.phase() == Phase.ANALYSIS)
                .filter(state -> state.failure().isPresent())
                .flatMap(State::retryTarget);
    }

    public synchronized Optional<ReissueTarget> claimSuccessfulDeliveryReissue(UUID documentId) {
        synchronized (successfulDeliveries) {
            return Optional.ofNullable(successfulDeliveries.remove(documentId));
        }
    }

    public synchronized void restoreSuccessfulDeliveryReissue(UUID documentId, ReissueTarget target) {
        synchronized (successfulDeliveries) {
            successfulDeliveries.putIfAbsent(documentId, target);
            evictOldestReissueTargets();
        }
    }

    private void rememberSuccessfulDelivery(UUID documentId, ReissueTarget target) {
        synchronized (successfulDeliveries) {
            successfulDeliveries.put(documentId, target);
            evictOldestReissueTargets();
        }
    }

    private void evictOldestReissueTargets() {
        while (successfulDeliveries.size() > MAX_REISSUE_TARGETS) {
            successfulDeliveries.remove(successfulDeliveries.keySet().iterator().next());
        }
    }

    public enum Phase {
        ANALYSIS,
        DELIVERY
    }

    public record RetryTarget(String executionId, long executionVersion) {
        public RetryTarget {
            if (executionId == null || executionId.isBlank()) {
                throw new IllegalArgumentException("executionId must not be blank");
            }
            if (executionVersion < 0) {
                throw new IllegalArgumentException("executionVersion must not be negative");
            }
        }
    }

    public record ReissueTarget(String executionId, long executionVersion, String commandId) {
        public ReissueTarget {
            if (executionId == null || executionId.isBlank() || commandId == null || commandId.isBlank()) {
                throw new IllegalArgumentException("reissue target identities must not be blank");
            }
            if (executionVersion < 0) {
                throw new IllegalArgumentException("executionVersion must not be negative");
            }
        }
    }

    public record State(Phase phase, Optional<InvoiceWorkflowFailure> failure, Optional<RetryTarget> retryTarget) {
        public State {
            if (phase == null || failure == null || retryTarget == null) {
                throw new IllegalArgumentException("execution state fields must not be null");
            }
            if (retryTarget.isPresent() && failure.isEmpty()) {
                throw new IllegalArgumentException("a retry target requires a failed execution");
            }
        }

        private static State running(Phase phase) {
            return new State(phase, Optional.empty(), Optional.empty());
        }

        private static State failed(Phase phase, InvoiceWorkflowFailure failure) {
            return new State(phase, Optional.of(failure), Optional.empty());
        }

        private static State failed(Phase phase, InvoiceWorkflowFailure failure, RetryTarget retryTarget) {
            return new State(phase, Optional.of(failure), Optional.of(retryTarget));
        }

        public boolean running() {
            return failure.isEmpty();
        }
    }
}
