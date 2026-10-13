package com.mokapotlabs.invoiceassistant.web;

import com.mokapotlabs.invoiceassistant.settings.*;
import io.quarkus.qute.*;
import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.net.URI;
import java.util.*;
import org.jboss.resteasy.reactive.RestForm;

@Path("/")
public class SettingsResource {
    private final SettingsRepository settingsRepository;

    public SettingsResource(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @CheckedTemplate(basePath = "")
    static class Templates {
        static native TemplateInstance setup(SettingsState state, String message);

        static native TemplateInstance settings(SettingsState state, String message);
    }

    @GET
    @Path("setup")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance setup() {
        return Templates.setup(settingsRepository.initializeAndLoad(), "");
    }

    @GET
    @Path("settings")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance settings() {
        return Templates.settings(settingsRepository.initializeAndLoad(), "");
    }

    @POST
    @Path("settings")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    @io.quarkus.security.Authenticated
    public Response save(
            @RestForm("filenameSeparator") String separator,
            @RestForm("autoApprove") boolean automatic,
            @RestForm("recipient") String recipient,
            @RestForm("subjectTemplate") String subject,
            @RestForm("bodyTemplate") String body,
            @RestForm("propertyId") List<String> ids,
            @RestForm("displayName") List<String> names,
            @RestForm("canonicalAddress") List<String> addresses,
            @RestForm("streetNumberPrefix") List<String> numbers,
            @RestForm("accountantRecipientOverride") List<String> overrides,
            @RestForm("enabled") List<String> enabled) {
        try {
            var previous = settingsRepository.initializeAndLoad().settings();
            if (ids.size() != names.size()
                    || ids.size() != addresses.size()
                    || ids.size() != numbers.size()
                    || ids.size() != overrides.size())
                throw new IllegalArgumentException("Property settings were incomplete.");
            List<ApplicationSettings.ConfiguredProperty> properties = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                var id = ids.get(i);
                var existing = previous.property(id)
                        .orElseThrow(() -> new IllegalArgumentException("Property identifiers cannot be changed."));
                properties.add(new ApplicationSettings.ConfiguredProperty(
                        id,
                        names.get(i),
                        addresses.get(i),
                        existing.aliases(),
                        numbers.get(i),
                        enabled.contains(id),
                        overrides.get(i)));
            }
            if (properties.size() != previous.properties().size())
                throw new IllegalArgumentException("Property settings were incomplete.");
            var settings = new ApplicationSettings(
                    1,
                    separator,
                    properties,
                    new ApplicationSettings.AccountantSettings(recipient, subject, body),
                    automatic);
            settingsRepository.save(settings);
            if (!settings.isReady())
                return Response.status(400)
                        .entity(Templates.setup(
                                settingsRepository.load(),
                                "Enable at least one property and enter a valid accountant recipient."))
                        .build();
            return Response.seeOther(URI.create("/")).build();
        } catch (IllegalArgumentException | java.io.IOException failure) {
            return Response.status(400)
                    .entity(Templates.settings(settingsRepository.initializeAndLoad(), failure.getMessage()))
                    .build();
        }
    }
}
