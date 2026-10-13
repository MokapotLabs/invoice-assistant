package com.mokapotlabs.invoiceassistant.delivery;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.Config;

@ApplicationScoped
public class DeliveryConfiguration {
    public enum Adapter {
        FAKE,
        GRAPH
    }

    private final Adapter adapter;

    public DeliveryConfiguration(Config config) {
        adapter = Adapter.valueOf(config.getValue("invoice-assistant.delivery.adapter", String.class)
                .trim()
                .toUpperCase(java.util.Locale.ROOT));
    }

    public Adapter adapter() {
        return adapter;
    }
}
