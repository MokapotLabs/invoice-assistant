# Third-party components

Apache-2.0 covers this application's authored code. Preserve license/copyright metadata of dependencies and the Apache-licensed Maven wrapper when distributing binaries. TPF and PDFBox use Apache-2.0; MSAL4J uses MIT; JNA offers Apache-2.0/LGPL dual licensing. Quarkus and the transitive runtime include additional components with their own notices. Inspect dependency POM licenses and bundled META-INF notices for the exact resolved artifacts before a binary release; this source audit is not a complete transitive legal review.

Ollama model weights are downloaded separately, not bundled in this repository. Review each model's license and hardware requirements before redistribution or commercial use. `tev1` is experimental. Its availability and license are controlled by its publisher.
