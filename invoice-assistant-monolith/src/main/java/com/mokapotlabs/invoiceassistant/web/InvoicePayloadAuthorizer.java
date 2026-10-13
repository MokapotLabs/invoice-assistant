package com.mokapotlabs.invoiceassistant.web;

import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.connector.PayloadBoundaryAuthorizationRequest;
import org.pipelineframework.connector.PayloadBoundaryAuthorizer;
import org.pipelineframework.connector.PayloadBoundaryOwner;

@ApplicationScoped
public class InvoicePayloadAuthorizer implements PayloadBoundaryAuthorizer {
    @Override
    public PayloadBoundaryOwner authorize(PayloadBoundaryAuthorizationRequest request) {
        String required = request.action() == PayloadBoundaryAuthorizationRequest.Action.UPLOAD
                ? "invoice.write"
                : "invoice.read";
        if (!LocalPayloadAuthentication.PRINCIPAL.equals(request.principalName())
                || !"local".equals(request.requestedTenantId())
                || !"invoices".equals(request.requestedScopeId())
                || !required.equals(request.authorizationScope())) {
            throw new SecurityException("The authenticated operator cannot access this invoice payload owner.");
        }
        return new PayloadBoundaryOwner("local", "invoices");
    }
}
