package com.mokapotlabs.invoiceassistant.web;

import com.mokapotlabs.invoiceassistant.readside.InvoiceReadUnavailableException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public final class InvoiceReadUnavailableExceptionMapper implements ExceptionMapper<InvoiceReadUnavailableException> {
    @Override
    public Response toResponse(InvoiceReadUnavailableException exception) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .type(MediaType.TEXT_PLAIN_TYPE)
                .entity(exception.getMessage())
                .build();
    }
}
