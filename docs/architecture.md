# Architecture

The source contains four build modules: common invoice contracts, local host/settings support, generated REST payload boundaries, and the invoice application. The public repository is MokapotLabs/invoice-assistant.

The business pipeline uses LOCAL transport and COMPUTE. A separate REST/COMPUTE boundary module produces authenticated upload/download routes in the same Quarkus application. PDF references cross the typed pipeline; bytes are materialized only at bounded extraction and attachment edges.

The flow is admission → text extraction Query → local bounded decision Query → supplier evidence route (optional image Query) → persisted review → conservative approval policy → deferred human confirmation where required → persisted confirmation → notification Command → persisted result. Pure services prepare and validate decisions. TPF owns deferred interactions, Query captures and Command attempt identity. The UI reads typed business tables and live execution observations.

The Graph adapter is selected by deployment configuration. Test fixtures select `fake`; production defaults to `graph`. Unconfigured authentication cannot fall back to simulated success. The Graph host connection owns OAuth and protected credentials; the business Command receives only the confirmed message and verified PDF capability. An uncertain submission is a terminal recorded attempt. Reissuing is an explicit user decision with a new occurrence identity.

Automatic approval is off by default. It requires high-confidence bounded choices plus independently checked literal invoice facts and one unambiguous configured address in PDF text, strong textual supplier evidence, no warnings, and captured ready email settings. Scores alone cannot authorize sending. Image-only evidence and uncertain cases wait for a human. Policy changes apply to newly admitted invoices.

The initial orchestrator/capture/effect stores use memory, while invoice reviews, confirmations and notification results persist in local H2. Do not assume an interrupted in-memory execution can resume after process restart: the read side reports an interrupted analysis and supports a deliberate fresh analysis. A process crash during an external send can leave no durable completion observation; check Outlook before recreating or resending that invoice.

Resend is offered only while TPF retains the successful execution and Command records. After an app restart with the default memory stores, the delivery page replaces unavailable resend controls with a message to check Sent Items and handle the invoice manually. Existing analysis, review and interrupted-delivery recovery remain available. Removing an invoice from the inbox retains its files, audit history and duplicate detection.
