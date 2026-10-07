# Chat session and Compose screen

`ChatOutbox.kt` owns one session's local receipts and retained prompt drafts.
ChatSession and ChatScreen use this owner; do not add a
second optimistic-message implementation to a composable.

## Public boundary

- Only `send` and explicit `retry` call the injected `PromptSender`. A null write
  leaves receipts and the caller's composer unchanged. Read-only instances never
  call it. Transport validation remains in PiClient/CommandBuilder.
- `acknowledge` requires the same session and a known pending/uncertain request.
  Accepted/failed receipts cannot be downgraded by duplicate ACK or stale timeout.
  Acceptance is not task completion.
- `restore` requires an empty composer, including attachments/preparation. A
  blocked restore retains all old bytes. Reentry has text receipts only and
  requires manual reattachment. It never automatically retries.
- `reconcile` consumes the canonical transcript through TranscriptReconciler;
  server echoes win while a SENDING request retains its pending ACK identity.
- `localReceipts` is the text-only persistence boundary. PendingMessages keeps
  the original private host/session preference key; PendingMessageCodec accepts
  old Java records and preserves image source indices in new records.

`ChatSession.kt` owns canonical transcript and presentation state, scoped
ACK/control/configuration/document/history requests, epoch guards, persisted
viewport requests and follow-tail. Its injected `Transport` has no credentials;
the Kotlin Activity composes it with PiClient. Foreign/unknown frames cannot
consume requests. History prepends stable keys without requesting a tail jump;
cached snapshots cannot fetch pages or replay prompts. A model change requires
an idle session; all mutations reject read-only inspection.

`ChatScreen.kt` owns Compose header, banners/loading, transcript, attachment
chips, skills, composer and delivery selection. Screen callbacks request platform
navigation, picker/permissions/dictation and bounded model/document interop.
The Activity preserves unsent text/caret on recreation. PiTranscript's default
contract stays read-only; main-chat actions are injected callbacks to ChatSession.
The left drawer is removed in0.6.005; the back action returns to the catalog.
Composer IME options stay stable while keyboard visibility changes; user dismissal
clears focus without changing text/caret. ChatSession owns dismissible inline
notices, never Toast/Snackbar. Accepted receipts render `read` below the bubble,
without implying task completion. Effort/model/tier panels remain open through
selections and configuration ACK; only explicit user dismissal closes them.
Transcription renders a spinner in composer and never automatically sends text.
The old Java screen/widgets/adapter/XML have been removed.

Mutations run on the Android main thread. This feature owns no connection,
permissions, Pi runtime or token. No second Activity/Composable outbox is allowed.

Checks: ChatSessionTest, ChatOutboxTest, PendingMessageCodecTest, ChatOutboxUiTest,
ExpressiveUiTest, DesignPreviewTest and EffortInteractionUiTest;
`npm run quality`; Android JVM/build and synthetic API35 instrumentation.
