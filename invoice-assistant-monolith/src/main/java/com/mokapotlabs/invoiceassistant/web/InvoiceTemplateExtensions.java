package com.mokapotlabs.invoiceassistant.web;

import io.quarkus.qute.TemplateExtension;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@TemplateExtension
public final class InvoiceTemplateExtensions {
    private InvoiceTemplateExtensions() {}

    public static String usd(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }
}
