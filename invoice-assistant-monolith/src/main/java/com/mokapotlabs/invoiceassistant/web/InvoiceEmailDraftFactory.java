package com.mokapotlabs.invoiceassistant.web;

import com.mokapotlabs.invoiceassistant.app.CanonicalFilenamePolicy;
import com.mokapotlabs.invoiceassistant.domain.InvoiceReview;
import com.mokapotlabs.invoiceassistant.domain.Property;
import com.mokapotlabs.invoiceassistant.readside.InvoiceReadModel;
import com.mokapotlabs.invoiceassistant.settings.ApplicationSettings;
import com.mokapotlabs.invoiceassistant.settings.SettingsRepository;
import com.mokapotlabs.invoiceassistant.settings.SettingsState;
import jakarta.enterprise.context.ApplicationScoped;

/** Builds the initial editable email projection; it is UI convenience, never execution authority. */
@ApplicationScoped
public final class InvoiceEmailDraftFactory {
    private final SettingsRepository settingsRepository;
    private final CanonicalFilenamePolicy filenames = new CanonicalFilenamePolicy();

    public InvoiceEmailDraftFactory(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public InvoiceEmailDraft create(InvoiceReadModel record) {
        InvoiceReview review = record.reviewState().review().orElseThrow();
        ApplicationSettings settings = settings();
        ApplicationSettings.ConfiguredProperty configured = settings.property(
                        review.recommendation().propertyId())
                .orElseThrow(() ->
                        new IllegalArgumentException("The recommended property is no longer present in Settings."));
        Property property = new Property(
                configured.id(),
                configured.displayName(),
                configured.canonicalAddress(),
                configured.aliases(),
                configured.streetNumberPrefix());
        String filename = filenames.filename(
                record.document(), property, review, review.facts().supplier(), settings.filenameSeparator());
        return new InvoiceEmailDraft(
                settings.accountantRecipient(property.id()),
                render(settings.accountant().subjectTemplate(), property, filename, review),
                render(settings.accountant().bodyTemplate(), property, filename, review));
    }

    static String render(String template, Property property, String filename, InvoiceReview review) {
        return template.replace("{property}", property.id())
                .replace("{property-street-number}", property.streetNumberPrefix())
                .replace("{filename}", filename)
                .replace("{supplier}", review.facts().supplier())
                .replace("{note}", review.note());
    }

    private ApplicationSettings settings() {
        SettingsState state = settingsRepository.load();
        if (state instanceof SettingsState.RepairRequired repair) {
            throw new IllegalStateException(repair.message());
        }
        return state.settings();
    }
}
