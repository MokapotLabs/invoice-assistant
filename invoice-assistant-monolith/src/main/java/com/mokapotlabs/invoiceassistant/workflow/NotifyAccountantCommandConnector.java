package com.mokapotlabs.invoiceassistant.workflow;

import com.mokapotlabs.invoiceassistant.app.AccountantNotification;
import com.mokapotlabs.invoiceassistant.app.NotifyAccountantResult;
import com.mokapotlabs.invoiceassistant.domain.NotificationCommandResult;
import com.mokapotlabs.invoiceassistant.domain.NotifyAttempt;
import com.mokapotlabs.invoiceassistant.domain.NotifyResult;
import com.mokapotlabs.invoiceassistant.storage.DeliveryOperationStatus;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.command.CommandConnector;
import org.pipelineframework.command.CommandRequest;

@ApplicationScoped
public class NotifyAccountantCommandConnector implements CommandConnector<NotifyAttempt, NotifyResult> {
    private final AccountantNotification accountantNotification;

    public NotifyAccountantCommandConnector(AccountantNotification accountantNotification) {
        this.accountantNotification = accountantNotification;
    }

    @Override
    public String command() {
        return "notify-accountant";
    }

    @Override
    public Uni<NotifyResult> execute(CommandRequest<NotifyAttempt> request) {
        return Uni.createFrom()
                .item(() -> {
                    var command = request.input().command();
                    NotifyAccountantResult result =
                            accountantNotification.notifyAccountant(command.document(), command.confirmedInvoice());
                    if (result.status() != DeliveryOperationStatus.SUCCEEDED
                            && result.status() != DeliveryOperationStatus.USER_ACTION_REQUIRED
                            && result.status() != DeliveryOperationStatus.SUBMISSION_UNCERTAIN) {
                        throw new DeliveryCommandFailure(result.status(), result.message());
                    }
                    return new NotifyResult(new NotificationCommandResult(command, result, request.occurrenceId()));
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
