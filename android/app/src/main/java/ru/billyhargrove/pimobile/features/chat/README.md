# Chat command state

`ChatOutbox.kt` owns one session's local receipts and retained prompt drafts.
ChatActivity and the future Compose screen must use this owner; do not add a
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

Mutations run on the Android main thread. This feature owns no connection,
permissions, navigation, Pi runtime, token or Markdown/media rendering. Native
chat/composer UI remains until its separately verified Compose slice.

Checks: ChatOutboxTest, PendingMessageCodecTest, ChatOutboxUiTest;
`npm run quality`; Android JVM/build and synthetic API35 instrumentation.
