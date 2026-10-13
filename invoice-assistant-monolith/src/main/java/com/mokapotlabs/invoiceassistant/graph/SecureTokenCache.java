package com.mokapotlabs.invoiceassistant.graph;

import java.util.Optional;

public interface SecureTokenCache {
    Optional<String> read();

    void write(String cache);

    void clear();
}
