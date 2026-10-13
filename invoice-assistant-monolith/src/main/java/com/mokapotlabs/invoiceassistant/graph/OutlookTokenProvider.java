package com.mokapotlabs.invoiceassistant.graph;

import java.util.Optional;

/** Host-only credential boundary; access tokens never enter pipeline state. */
public interface OutlookTokenProvider {
    Optional<String> accessToken();

    void requireReconnect();
}
