# Invoice Assistant

A local, attended invoice assistant for property managers. Upload one PDF or a batch, extract invoice details with Ollama, review the proposed property and email, and send the PDF to your accountant through a personal Outlook.com account.

The application is built on The Pipeline Framework **26.10.1-SNAPSHOT**. Its typed pipeline owns Queries, deferred confirmation, Commands, and the persisted audit trail. PDF upload and download use generated authenticated HTTP payload boundaries. Automatic approval is **off by default**: select **Approve if confident** beside the upload button for newly uploaded invoices. Missing facts, low-confidence decisions, ambiguous addresses, extraction warnings, or unavailable delivery settings require review.

## Run locally

The first release supports **macOS**, Java **25**, and [Ollama 0.35 or newer](https://ollama.com/blog/ollama-now-supports-jev-style-decision-models). Outlook credentials persist in macOS Keychain; there is no plaintext fallback on other operating systems. Hardware must accommodate the configured models. `tev1` is experimental and its scores are not a guarantee of correctness.

```sh
ollama pull qwen3.5:4b
ollama pull qwen3-vl:4b-instruct
ollama pull tev1
./mvnw package
export INVOICE_ASSISTANT_GRAPH_CLIENT_ID=your-public-client-application-id
java --enable-native-access=ALL-UNNAMED \
  -Dinvoice-assistant.http.port=8080 \
  -jar invoice-assistant-monolith/target/quarkus-app/quarkus-run.jar
```

Open <http://127.0.0.1:8080/setup>. Configure property addresses, an accountant recipient, and the email templates. The starter recipient is intentionally empty. Open [Connect Outlook](http://127.0.0.1:8080/outlook) and complete Microsoft's device-code sign-in. After setup, use **Catalogue** for properties and filename settings, and **Recipient** for email delivery settings. No client secret is needed. The local session cookie is issued by the attended loopback UI. Data is stored under `~/.invoice-assistant`, separately from source. To customize or add properties, stop the application and edit `~/.invoice-assistant/config/settings.yaml`; identifiers must be unique. The form edits existing properties.

Text extraction uses `qwen3.5:4b`, supplier image fallback uses `qwen3-vl:4b-instruct`, and the bounded supplier/property decision uses local `tev1` through Ollama's `/v1/systemone` protocol. Models and addresses are declared in `config/pipeline.yaml`. Keep the decision endpoint's base URL at `http://localhost:11434`. All invoice AI analysis runs locally with these defaults. Sending email and Microsoft authentication require the internet; approved invoice PDFs are sent to Microsoft and the configured recipient.

## Outlook registration

1. Register a public-client application in a stable Microsoft Entra tenant. Select **Personal Microsoft accounts** as the supported account audience.
2. Under Authentication, allow public client flows for device-code authentication.
3. Add only Microsoft Graph **delegated `Mail.Send`** as a mailbox permission. Remove any default `User.Read` permission. Copy the application/client ID to `INVOICE_ASSISTANT_GRAPH_CLIENT_ID`.
4. Sign in with the Outlook.com mailbox that should send invoices. The authority is `https://login.microsoftonline.com/consumers/`. MSAL also uses standard identity/refresh-token scopes; the app requests no mailbox-reading permission.

[Microsoft sendMail documentation](https://learn.microsoft.com/en-us/graph/api/user-sendmail?view=graph-rest-1.0) supports delegated `Mail.Send` for personal accounts. No paid mailbox subscription is needed for this personal-account path. Microsoft controls app-registration access and mailbox throttling.

Each PDF is limited to **2,900,000 bytes** and the encoded message to **3,900,000 bytes**. The app uses small file attachments and saves submitted messages to Sent Items. Graph's `202` response means **accepted for processing**; it does not confirm recipient delivery or verify Sent Items. Timeouts and server errors are recorded as an uncertain send and are never automatically resent. Check Outlook before deliberately sending again; the UI requires acknowledgement of possible duplication. Consent expiry or revocation shows **Connect Outlook**.

The recipient must be in the explicit recipient allowlist (default accountant address or an enabled property's override). Changing the proposed message does not add a recipient to that allowlist. Auto-approved messages use delivery settings captured at upload and the current allowlist is checked again before submission.

## Development and verification

```sh
./mvnw test
```

Tests use synthetic PDFs, deterministic Query fixtures, and a loopback Graph fixture; they never send real mail. An optional macOS Keychain fixture uses an isolated random cache entry:

```sh
RUN_KEYCHAIN_FIXTURES=true ./mvnw -pl invoice-assistant-monolith -am   -Dtest=MacKeychainTokenCacheTest -Dsurefire.failIfNoSpecifiedTests=false test
```

This public edition is an adaptation of an existing local tool. Its new Git history records the actual public introduction slices, with current dates; it does not imply that the underlying project originated for a challenge. See [architecture](docs/architecture.md), [security](SECURITY.md), and [release audit](docs/release-audit.md).

## License

[Apache License 2.0](LICENSE). Dependency and model licenses remain separate; see [third-party notices](docs/third-party.md).

The initial local decision configuration supports up to **25 enabled properties**. It always includes a no-supported-property choice, including when there is only one configured property; that choice forces human review.
