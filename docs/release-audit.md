# Public export audit — 2026-10-13

Target repository: **MokapotLabs/invoice-assistant**. This is an email-only adaptation of an existing local application, licensed under Apache-2.0. It is prepared locally and has not been published to GitHub.

## Source and history

The export was created from an explicit source allowlist in a separate directory. No previous `.git` directory, commit history, tags, object alternates, runtime data, mailbox caches, invoice PDFs, production configuration, private audit notes, unrelated applications, or old installers were copied. Legacy external browser automation and remote AI configuration are absent from every public commit. Only fictional starter addresses and example recipients remain. The starter accountant recipient is empty and automatic approval is off.

The new history uses the actual current dates and a public GitHub no-reply author address. Commits introduce the build/contracts, complete typed email application, test/setup documentation, and the unmatched-property safety correction. They do not represent an invented development chronology or a claim of challenge eligibility. One fresh root commit anchors the history; no private ancestors or shared object storage exist.

Gitleaks **8.30.1**, with redacted output, reported zero credential findings across source and all prepared public commits. A separate explicit privacy scan checked for retained private hostnames, personal paths, removed integration identifiers, and non-example recipient addresses. It reported zero findings. Generated build output is ignored and excluded from the source/history inventory. `source-manifest.sha256` records SHA-256 hashes of the exported files, excluding itself.

## Verification

- The existing application reactor passed 128 tests; two separately gated fixtures were skipped in that run. Its seven external-browser fixtures and one authenticated batch-upload UI fixture then passed against local synthetic pages.
- The public reactor passed 22 tests; the optional native Keychain fixture was skipped. That fixture then passed separately, creating, updating, reading and deleting an isolated synthetic cache entry.
- Public `verify` produced the runnable Quarkus package. Production startup and the authenticated Outlook status/setup pages were checked using an isolated synthetic data directory.
- The pinned TPF decision client passed a native `/v1/systemone` protocol fixture. Real `tev1` requests succeeded, including the one-property catalogue with an explicit no-supported-property alternative. A no-match or low-confidence result forces review. The initial model contract supports up to 25 enabled properties.
- A synthetic PDF completed the packaged real local extraction and decision flow in 111 seconds and reached deferred review, with no confirmation or notification outcome. This verifies the local path; it is not an accuracy benchmark.
- Tests cover owned upload/download, owner rejection, changed PDF bytes, metadata mismatch, recipient allowlisting, attachment limits, Graph acceptance, revoked authorization, uncertain non-retryable submission, disconnect cache removal, opt-in approval, opt-out review, carried decision identity and manual email-only completion.

Microsoft OAuth consent and real email delivery were not exercised: those require an app registration and an explicitly chosen test mailbox. All automated mail tests used a loopback server and synthetic content. No customer invoice or real token was used. The protected token-cache implementation currently supports macOS only.

The application's main execution/capture/effect stores are in memory; its invoice audit tables persist in local H2. A crash during an external send can leave an unobserved outcome. Check Outlook before deliberately recreating or resending an interrupted invoice. Full transitive dependency licensing and vulnerability assessment remains a separate step before distributing binaries; this audit covers source sanitisation, history isolation and behavior verification.
