# Public export audit — 2026-10-13

Target repository: **MokapotLabs/invoice-assistant**. This is an email-only adaptation of an existing local application, licensed under Apache-2.0. The initial export audit was recorded on 2026-10-13; publication verification is recorded below.

## Source and history

The export was created from an explicit source allowlist in a separate directory. No previous `.git` directory, commit history, tags, object alternates, runtime data, mailbox caches, invoice PDFs, production configuration, private audit notes, unrelated applications, or old installers were copied. Legacy external browser automation is absent from the public history. The initial export omitted remote AI configuration; optional, generic OpenRouter bindings were added subsequently as documented below. Only fictional starter addresses and example recipients remain. The starter accountant recipient is empty and automatic approval is off.

The new history uses the actual current dates and a public GitHub no-reply author address. Commits introduce the build/contracts, complete typed email application, test/setup documentation, and the unmatched-property safety correction. They do not represent an invented development chronology or a claim of challenge eligibility. One fresh root commit anchors the history; no private ancestors or shared object storage exist.

Gitleaks **8.30.1**, with redacted output, reported zero credential findings across source and all prepared public commits. A separate explicit privacy scan checked for retained private hostnames, personal paths, removed integration identifiers, and non-example recipient addresses. It reported zero findings. Generated build output is ignored and excluded from the source/history inventory. `source-manifest.sha256` records SHA-256 hashes of the exported files, excluding itself.

## Verification

- The existing application reactor passed 128 tests; two separately gated fixtures were skipped in that run. Its seven external-browser fixtures and one authenticated batch-upload UI fixture then passed against local synthetic pages.
- The public reactor passed 22 tests; the optional native Keychain fixture was skipped. That fixture then passed separately, creating, updating, reading and deleting an isolated synthetic cache entry.
- Public `verify` produced the runnable Quarkus package. Production startup and the authenticated Outlook status/setup pages were checked using an isolated synthetic data directory.
- The pinned TPF decision client passed a native `/v1/systemone` protocol fixture. Real `tev1` requests succeeded, including the one-property catalogue with an explicit no-supported-property alternative. A no-match or low-confidence result forces review. The initial model contract supports up to 25 enabled properties.
- A synthetic PDF completed the packaged real local extraction and decision flow in 111 seconds and reached deferred review, with no confirmation or notification outcome. This verifies the local path; it is not an accuracy benchmark.
- Tests cover owned upload/download, owner rejection, changed PDF bytes, metadata mismatch, recipient allowlisting, attachment limits, Graph acceptance, revoked authorization, uncertain non-retryable submission, disconnect cache removal, opt-in approval, opt-out review, carried decision identity and manual email-only completion.

The automated audit does not exercise Microsoft OAuth consent or real email delivery; those require an app registration and an explicitly chosen test mailbox. All automated mail tests used a loopback server and synthetic content. No customer invoice or real token was used. The protected token-cache implementation currently supports macOS only.

The application's main execution/capture/effect stores are in memory; its invoice audit tables persist in local H2. A crash during an external send can leave an unobserved outcome. Check Outlook before deliberately recreating or resending an interrupted invoice. Full transitive dependency licensing and vulnerability assessment remains a separate step before distributing binaries; this audit covers source sanitisation, history isolation and behavior verification.

## Publication verification — 2026-10-19

The application source includes the subsequent catalogue/recipient split, opt-in approval layout, review and interrupted-delivery recovery, and explicit manual-handling guidance when resend is unavailable after a restart. The local defaults remain email-only Graph delivery and Ollama analysis, with no durable storage added.

- `./mvnw -U verify` passed with Java 25 and an initially empty, isolated Maven repository. Framework dependencies resolved from the public snapshot repository, and the runnable Quarkus package was produced. All 30 tests completed with zero failures or errors; the opt-in macOS Keychain fixture was skipped.
- Gitleaks 8.30.1, downloaded from its official release and checked against its published SHA-256 checksum, reported zero credential findings across the complete public history. An explicit scan of historical blobs found no retained private hostnames, personal filesystem paths, private app-registration identifiers, removed integration implementations, or non-example recipient addresses.
- The public history has one independent root, no commits shared with the private application's history, and no Git object alternates. Only the public `main` branch is selected for publication; private application history, runtime data, demo PDFs, token caches, and generated build output are excluded.
- `docs/source-manifest.sha256` was regenerated for the tracked source and documentation, excluding the manifest itself. Apache-2.0 licensing and the original public author attribution are retained.

This verification publishes source, not a binary release. It does not claim complete transitive dependency licensing or vulnerability assessment, or challenge eligibility.

## Optional OpenRouter verification — 2026-10-19

Ollama remains the default for all three AI Queries. A nonblank `OPENROUTER_API_KEY` now selects the packaged OpenRouter bindings for text, supplier images and bounded decisions together at startup. Credentials remain outside the runtime YAML. Both deployments keep the compiled connector operation identities unchanged.

- Java 25 `clean verify` passed: 38 tests, zero failures or errors, with the optional macOS Keychain fixture skipped.
- Loopback HTTP fixtures exercised the real pinned text/image Query operation and streaming SDK in both modes, including strict structured output, image bytes, local versus remote authentication and local thinking controls. The pinned decision client exercised both Ollama `/v1/systemone` and OpenRouter `/api/alpha/decisions` routing with bounded answers.
- The packaged application started in isolated data storage without a key, with a synthetic nonblank key, and again with a blank key. All three runtime bindings selected the expected deployment on each restart, and no credential was written to the runtime pipeline YAML. Each temporary application was stopped after verification.
- No live OpenRouter request or real email was sent by these checks. Documentation covers both modes and the external data processing and usage charges of OpenRouter mode. The source manifest was refreshed for the changed source and documentation.
