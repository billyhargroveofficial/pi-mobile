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
an idle session; all mutations reject read-only inspection. On start, owned request
IDs are reconciled with the current scoped transport ledger: disappeared results
become uncertain, never replayed or invented as acceptance. Live tickets remain
pending. Catch-up marks added/changed rows for a bounded opacity reveal; ordinary
streaming text updates do not restart it. Reader drag disables follow-tail; an unobtrusive New activity label discloses new
events beside the existing jump action without moving the reader.
Viewport restoration runs once, including old progress-prefixed keys.

`ChatScreen.kt` owns the measured overlay canvas, reader viewport and follow-tail.
Internal `ChatHeader.kt`, `ChatComposer.kt` and `ChatWorkDock.kt` render header/
notices, draft/attachments/delivery, and work/Queue/skills respectively. They use
shared `ui/PiIconButton` and `core/AgentMetrics`; state remains in the same owners.
Screen callbacks request platform
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
ExpressiveUiTest, DesignPreviewTest, EffortInteractionUiTest, RuntimeStabilityTest
(real Markdown/media/scoped WS/ticker counters), ChatArrivalUiTest (pixel anchors,
actual Activity stop/start, hardware opacity frames and measured tail movement);
`npm run quality`; Android JVM/build and synthetic API35 instrumentation.

## Configuration panels

`ModelCapabilities` is the pure, bounded capability projection shared by both
configuration panels, EffortSlider and TierToggle. It copies only reported valid
levels/tiers, preserves level order, rejects missing registry identities and
deduplicates canonical model keys before parsing capability arrays. UI never
rescans registry JSON on an effort step or search composition.

`ModelSettingsSession` owns the immutable catalog, query/filtered rows, selection,
capability-normalized draft, pending Apply and inline error. Only explicit Apply
emits its injected selection callback; closed, busy and pending drafts cannot
send. ACK re-enables the same panel; failure keeps the draft for explicit retry.
`ModelSettingsScreen` renders this state; `ui/ModelSettingsSheet` hosts the modal.

`QuickEffortSession` owns preview/commit, tier selection, pending status and
confirmed rollback. A successful ACK confirms only the submitted field when no newer report of that
field exists. Partial configuration cannot confirm an unrelated pending field. Failed changes
restore the last confirmed values without sending another command; an unknown
model has no editable capabilities. `QuickEffortScreen` renders the state and
binds the immutable levels to the native slider. `ui/EffortPopup` retains only
anchor geometry, lifecycle/back/insets, animation and callbacks. Closing a panel
retires its owner; results for a panel that never sent a change are ignored.
If the originating panel was closed before its result, Activity routes a failure
to the chat notice instead of another panel. ChatSession remains the sole scope/ACK/transport owner; these panels do not invent
request IDs or duplicate the command ledger. The source guard prohibits Android,
transport and renderer imports in state, and reverse state/screen dependencies.

Checks: ModelCapabilitiesTest, ModelSettingsSessionTest, QuickEffortSessionTest,
ConfigurationPanelUiTest, EffortInteractionUiTest and existing HotfixUiTest /
ExpressiveUiTest / DesignPreviewTest configuration scenarios. Keep actual finger,
keyboard/accessibility, IME, ACK/error persistence and display/font/theme checks.
