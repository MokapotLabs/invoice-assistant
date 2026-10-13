package com.mokapotlabs.invoiceassistant.domain;

public sealed interface InvoiceApprovalRoute permits ReviewReady, CompletedConfirmation {}
