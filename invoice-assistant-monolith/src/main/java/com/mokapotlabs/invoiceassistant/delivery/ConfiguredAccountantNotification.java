package com.mokapotlabs.invoiceassistant.delivery;

import com.mokapotlabs.invoiceassistant.app.AccountantNotification;
import com.mokapotlabs.invoiceassistant.app.NotifyAccountantResult;
import com.mokapotlabs.invoiceassistant.domain.ConfirmedInvoice;
import com.mokapotlabs.invoiceassistant.domain.InvoiceDocument;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ConfiguredAccountantNotification implements AccountantNotification {
    private final DeliveryConfiguration configuration;
    private final FakeAccountantNotification fakeAccountantNotification;
    private final GraphAccountantNotification graphAccountantNotification;

    public ConfiguredAccountantNotification(
            DeliveryConfiguration configuration,
            FakeAccountantNotification fakeAccountantNotification,
            GraphAccountantNotification graphAccountantNotification) {
        this.graphAccountantNotification = graphAccountantNotification;
        this.configuration = configuration;
        this.fakeAccountantNotification = fakeAccountantNotification;
    }

    @Override
    public NotifyAccountantResult notifyAccountant(InvoiceDocument document, ConfirmedInvoice confirmedInvoice) {
        return switch (configuration.adapter()) {
            case FAKE -> fakeAccountantNotification.notifyAccountant(document, confirmedInvoice);
            case GRAPH -> graphAccountantNotification.notifyAccountant(document, confirmedInvoice);
        };
    }
}
