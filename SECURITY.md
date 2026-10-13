# Security

This is an attended local macOS application. Keep the HTTP listener on loopback. The local session assumes the operating-system user is trusted; do not expose this server on a network or reverse proxy.

Owned PDF references are signed, owner-scoped and checked against admitted length/checksum before attachment materialization. The signing key has owner-only file permissions. Treat the local data directory, logs and database as private: they can contain invoice contents, proposed messages and recipients. Keep it out of Git and back it up with encrypted storage.

Microsoft tokens use native macOS Keychain APIs. The application uses a public client, device-code consent and delegated Mail.Send, with no client secret. Tokens are never emitted through the connection-status API or passed in subprocess arguments. Disconnect removes the protected local cache; removing consent at Microsoft is a separate account action.

Use GitHub's private vulnerability reporting for this repository when available. Do not attach invoice PDFs, token caches, environment files or unredacted logs to public issues.

Automatic approval is optional, conservative, and not an accounting guarantee. Review unfamiliar suppliers and property configurations. An uncertain email outcome requires checking Outlook before a deliberate new send.
