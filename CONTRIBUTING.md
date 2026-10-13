# Contributing

Use Java 25 and the Maven wrapper. Run `./mvnw test` with synthetic fixtures before submitting a pull request. Tests must never mutate a real mailbox. Keep examples fictional and credentials out of source and issue reports.

Use the TPF authoring skill for pipeline changes and verify syntax against the pinned 26.10.1-SNAPSHOT compiler sources/tests. `await:` is a modifier on an ordinary authored operation. Keep I/O behind typed Query/Command or framework-owned file boundaries; do not add application workflow machinery.

Contributions are licensed under Apache-2.0. Describe the behavior changed and the evidence verifying it. Format only touched Java files. The current Palantir formatter plugin requires Java 21 for formatting, while compilation and tests require Java 25.
