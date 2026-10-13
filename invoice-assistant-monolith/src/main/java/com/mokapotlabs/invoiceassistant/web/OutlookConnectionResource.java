package com.mokapotlabs.invoiceassistant.web;

import com.mokapotlabs.invoiceassistant.graph.GraphOutlookConnection;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;

@Path("/outlook")
@Authenticated
@Blocking
public class OutlookConnectionResource {
    private final GraphOutlookConnection connection;

    public OutlookConnectionResource(GraphOutlookConnection connection) {
        this.connection = connection;
    }

    @CheckedTemplate(basePath = "")
    static class Templates {
        static native TemplateInstance outlookConnection(GraphOutlookConnection.State state);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance page() {
        return Templates.outlookConnection(connection.state());
    }

    @GET
    @Path("status")
    @Produces(MediaType.APPLICATION_JSON)
    public GraphOutlookConnection.State status() {
        return connection.state();
    }

    @POST
    @Path("connect")
    public Response connect() {
        connection.connect();
        return Response.seeOther(URI.create("/outlook")).build();
    }

    @POST
    @Path("disconnect")
    public Response disconnect() {
        connection.disconnect();
        return Response.seeOther(URI.create("/outlook")).build();
    }
}
