package com.mokapotlabs.invoiceassistant.domain;

/** Qualitative supplier-evidence branch before the authoritative human confirmation. */
public sealed interface AnalysisRoute permits ReviewReady, VisionRasterizationRequest {}
