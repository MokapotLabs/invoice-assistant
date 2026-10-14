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

        static native TemplateInstance catalogue(SettingsState state, String message, boolean saved);

        static native TemplateInstance recipient(SettingsState state, String message, boolean saved);
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
    public Response settings() {
        return Response.seeOther(URI.create("/catalogue")).build();
    }

    @GET
    @Path("catalogue")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance catalogue(@QueryParam("saved") @DefaultValue("false") boolean saved) {
        return Templates.catalogue(settingsRepository.initializeAndLoad(), "", saved);
    }

    @GET
    @Path("recipient")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance recipient(@QueryParam("saved") @DefaultValue("false") boolean saved) {
        return Templates.recipient(settingsRepository.initializeAndLoad(), "", saved);
    }

    @POST
    @Path("catalogue")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    @io.quarkus.security.Authenticated
    public Response saveCatalogue(
            @RestForm("filenameSeparator") @DefaultValue("") String separator,
            @RestForm("propertyId") List<String> ids,
            @RestForm("displayName") List<String> names,
            @RestForm("canonicalAddress") List<String> addresses,
            @RestForm("streetNumberPrefix") List<String> numbers,
            @RestForm("enabled") List<String> enabled) {
        try {
            settingsRepository.update(previous -> new ApplicationSettings(
                    previous.schemaVersion(),
                    separator,
                    properties(previous, ids, names, addresses, numbers, enabled),
                    previous.accountant(),
                    previous.autoApprove()));
            return Response.seeOther(URI.create("/catalogue?saved=true")).build();
        } catch (IllegalArgumentException | java.io.IOException failure) {
            return Response.status(400)
                    .entity(Templates.catalogue(settingsRepository.load(), failure.getMessage(), false))
                    .build();
        }
    }

    @POST
    @Path("recipient")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    @io.quarkus.security.Authenticated
    public Response saveRecipient(
            @RestForm("recipient") @DefaultValue("") String recipient,
            @RestForm("subjectTemplate") @DefaultValue("") String subject,
            @RestForm("bodyTemplate") @DefaultValue("") String body,
            @RestForm("recipientPropertyId") List<String> ids,
            @RestForm("accountantRecipientOverride") List<String> overrides) {
        try {
            settingsRepository.update(previous -> new ApplicationSettings(
                    previous.schemaVersion(),
                    previous.filenameSeparator(),
                    recipients(previous, ids, overrides),
                    accountant(recipient, subject, body),
                    previous.autoApprove()));
            return Response.seeOther(URI.create("/recipient?saved=true")).build();
        } catch (IllegalArgumentException | java.io.IOException failure) {
            return Response.status(400)
                    .entity(Templates.recipient(settingsRepository.load(), failure.getMessage(), false))
                    .build();
        }
    }

    @POST
    @Path("preferences/auto-approval")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    @Blocking
    @io.quarkus.security.Authenticated
    public Response saveApproval(@RestForm("enabled") boolean enabled) {
        try {
            settingsRepository.update(previous -> new ApplicationSettings(
                    previous.schemaVersion(),
                    previous.filenameSeparator(),
                    previous.properties(),
                    previous.accountant(),
                    enabled));
            return Response.noContent().build();
        } catch (IllegalArgumentException | java.io.IOException failure) {
            return Response.status(400)
                    .entity("The approval preference could not be saved.")
                    .build();
        }
    }

    @POST
    @Path("setup")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    @io.quarkus.security.Authenticated
    public Response saveSetup(
            @RestForm("filenameSeparator") @DefaultValue("") String separator,
            @RestForm("recipient") @DefaultValue("") String recipient,
            @RestForm("subjectTemplate") @DefaultValue("") String subject,
            @RestForm("bodyTemplate") @DefaultValue("") String body,
            @RestForm("propertyId") List<String> ids,
            @RestForm("displayName") List<String> names,
            @RestForm("canonicalAddress") List<String> addresses,
            @RestForm("streetNumberPrefix") List<String> numbers,
            @RestForm("enabled") List<String> enabled,
            @RestForm("recipientPropertyId") List<String> recipientIds,
            @RestForm("accountantRecipientOverride") List<String> overrides) {
        try {
            settingsRepository.update(previous -> {
                var catalogue = new ApplicationSettings(
                        previous.schemaVersion(),
                        separator,
                        properties(previous, ids, names, addresses, numbers, enabled),
                        previous.accountant(),
                        previous.autoApprove());
                return new ApplicationSettings(
                        catalogue.schemaVersion(),
                        separator,
                        recipients(catalogue, recipientIds, overrides),
                        accountant(recipient, subject, body),
                        previous.autoApprove());
            });
            if (!settingsRepository.load().settings().isReady())
                return Response.status(400)
                        .entity(Templates.setup(
                                settingsRepository.load(), "Enable at least one property and enter a valid recipient."))
                        .build();
            return Response.seeOther(URI.create("/")).build();
        } catch (IllegalArgumentException | java.io.IOException failure) {
            return Response.status(400)
                    .entity(Templates.setup(settingsRepository.load(), failure.getMessage()))
                    .build();
        }
    }

    private static List<ApplicationSettings.ConfiguredProperty> properties(
            ApplicationSettings previous,
            List<String> ids,
            List<String> names,
            List<String> addresses,
            List<String> numbers,
            List<String> enabled) {
        validateIds(previous, ids);
        if (ids.size() != names.size() || ids.size() != addresses.size() || ids.size() != numbers.size())
            throw new IllegalArgumentException("Property settings were incomplete.");
        List<ApplicationSettings.ConfiguredProperty> properties = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            var existing = previous.property(ids.get(i)).orElseThrow();
            properties.add(new ApplicationSettings.ConfiguredProperty(
                    existing.id(),
                    names.get(i),
                    addresses.get(i),
                    existing.aliases(),
                    numbers.get(i),
                    enabled.contains(existing.id()),
                    existing.accountantRecipientOverride()));
        }
        return properties;
    }

    private static List<ApplicationSettings.ConfiguredProperty> recipients(
            ApplicationSettings previous, List<String> ids, List<String> overrides) {
        validateIds(previous, ids);
        if (ids.size() != overrides.size()) throw new IllegalArgumentException("Recipient settings were incomplete.");
        return previous.properties().stream()
                .map(property -> new ApplicationSettings.ConfiguredProperty(
                        property.id(),
                        property.displayName(),
                        property.canonicalAddress(),
                        property.aliases(),
                        property.streetNumberPrefix(),
                        property.enabled(),
                        overrides.get(ids.indexOf(property.id()))))
                .toList();
    }

    private static ApplicationSettings.AccountantSettings accountant(String recipient, String subject, String body) {
        var value = new ApplicationSettings.AccountantSettings(recipient, subject, body);
        if (!value.isReady()) throw new IllegalArgumentException("Enter a valid recipient email address.");
        return value;
    }

    private static void validateIds(ApplicationSettings previous, List<String> ids) {
        var expected = previous.properties().stream()
                .map(ApplicationSettings.ConfiguredProperty::id)
                .collect(java.util.stream.Collectors.toSet());
        if (ids.size() != expected.size() || !new HashSet<>(ids).equals(expected))
            throw new IllegalArgumentException("Property identifiers cannot be changed or omitted.");
    }
}
