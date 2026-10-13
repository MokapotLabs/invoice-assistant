package com.mokapotlabs.assistantlocal.app;

import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

/** A tiny loopback-only readiness check used when a second Finder launch reuses the running UI. */
@Path("/health")
public class LocalHealthResource {
    @GET
    @Blocking
    public Response health() {
        return Response.ok().build();
    }
}
