# Invoice Assistant

A local, attended invoice assistant for property managers. Upload one PDF or a batch, extract invoice details with Ollama or OpenRouter, review the proposed property and email, and send the PDF to your accountant through a personal Outlook.com account.

The application is built on The Pipeline Framework **26.10.1-SNAPSHOT**. Its typed pipeline owns Queries, deferred confirmation, Commands, and the persisted audit trail. PDF upload and download use generated authenticated HTTP payload boundaries. Manual confirmation requests remain open for 30 days while the application stays running.

Automatic approval is **off by default**: select **Approve if confident** beside the upload button for newly uploaded invoices. Missing facts, low-confidence decisions, ambiguous addresses, extraction warnings, or unavailable delivery settings require review.

## Run locally

The first release supports **macOS** and Java **25**. The default local mode requires [Ollama 0.35 or newer](https://ollama.com/blog/ollama-now-supports-jev-style-decision-models); OpenRouter mode does not require Ollama. Outlook credentials persist in macOS Keychain; there is no plaintext fallback on other operating systems. In local mode, hardware must accommodate the configured models. `tev1` is experimental and decision scores are not a guarantee of correctness.

### Ollama (default)

Leave `OPENROUTER_API_KEY` unset or blank to keep all three models local:

```sh
unset OPENROUTER_API_KEY
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

Text extraction uses `qwen3.5:4b`, supplier image fallback uses `qwen3-vl:4b-instruct`, and the bounded supplier/property decision uses local `tev1`. Text and images use Ollama's [OpenAI-compatible endpoint](https://docs.ollama.com/api/openai-compatibility) at `http://localhost:11434/v1`; decisions use `/v1/systemone`, with the decision binding's base URL at `http://localhost:11434`. Local model bindings are declared in `config/pipeline.yaml`. All AI analysis stays local in this mode. Sending email and Microsoft authentication still require the internet; approved invoice PDFs are sent to Microsoft and the configured recipient.

### OpenRouter (optional)

Set a nonblank `OPENROUTER_API_KEY` in the environment of the application process. The same build automatically switches **all three** model bindings when it starts; Ollama and local model downloads are unnecessary:

```sh
./mvnw package
export OPENROUTER_API_KEY=your-openrouter-api-key
export INVOICE_ASSISTANT_GRAPH_CLIENT_ID=your-public-client-application-id
java --enable-native-access=ALL-UNNAMED \
  -Dinvoice-assistant.http.port=8080 \
  -jar invoice-assistant-monolith/target/quarkus-app/quarkus-run.jar
```

| Query | Ollama default | OpenRouter |
| --- | --- | --- |
| Invoice text | `qwen3.5:4b` | `google/gemini-3.1-flash-lite` |
| Supplier images/logos | `qwen3-vl:4b-instruct` | `google/gemini-3.1-flash-lite` |
| Supplier/property decision | `tev1` | `typesafe/jev-1.13` |

The OpenRouter defaults use [Gemini Flash Lite](https://openrouter.ai/google/gemini-3.1-flash-lite) for extraction and vision, and [Jev](https://openrouter.ai/typesafe/jev-1.13) for the bounded decision. Their non-secret bindings are declared in `config/openrouter.yaml`. Changing model declarations requires rebuilding; switching between the two packaged configurations only requires restarting the app. Unset the key and restart to return to Ollama.

OpenRouter mode sends invoice text, relevant catalogue details, and any supplier images used by the vision fallback to OpenRouter and its model providers. Internet access and sufficient OpenRouter credit are required. Credentials stay in the process environment and authenticated host connections; the app never writes the key to its runtime pipeline YAML. Do not commit it or put it in a launch command's arguments. An invalid key or a provider failure is reported as an analysis failure; the app does not silently fall back to local models.

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

Expired and restarted reviews resume their saved analysis and checksum-verified original catalogue when confirmed,
without another LLM call or reapplying automatic approval. Approved invoices lead to delivery and repeated confirmation
submissions leave the approved message unchanged. When the previous execution is unavailable, recovery requires
checking Outlook Sent Items and accepting a possible duplicate. Automatic resend is offered only while TPF retains
the successful execution and Command records. After an app restart with the default memory stores, the delivery page
shows manual-handling guidance when resend is unavailable. Removing an invoice from the inbox keeps its files,
audit history and duplicate detection.
