# 005 — Complete Android rewrite before integrated verification

Goal: [001](../current-todo/001-todo.md). Work directly in main. Billy's latest
instruction is to finish rewriting all remaining code/screens, then run the
integrated checks once; stop repeated per-slice UI/unit suites. Compilation with
bounded workers/RAM is allowed to resolve actual errors. No emulator during the
rewrite. The initial publication hold was superseded by Billy's explicit0.6.004 release and Telegram authorization below. Live deployment remains out of scope.

## Inventory and acceptance

67 remaining Java production files, about 5,800 lines, are enumerated by the
exact class ownership map in architecture/owners.json. Move each implementation
to Kotlin or replace unreachable Views with feature-owned Compose; preserve
wire/API behavior, private settings/cache/drafts, lifecycle and signing/version.
Preserve read-only inspection, no replay, session/request/epoch guards and one
live Pi. Use the supplied ChatGPT screens for drawer/grouped settings/chat and
the inspected video for the refined effort control. Do not fabricate unsupported
Pi features or false account/model/status/usage data.

- [x] Core: Ack/AckParser, Catalog/CatalogParser/CatalogRow, ChatMessage,
  CommandBuilder, ConnectionState, ConversationFrames, DurationText,
  EndpointPolicy, ExecutionInfo, ImageGuard/ImageMimeType/ImagePayload/ImageRef,
  MathMarkdown, MediaUrlPolicy, MessagesParser/MessagesUpdate, OrchestrationData,
  PayloadCodec, ReleaseUpdate, Session/SessionGrouping/SessionStatus,
  Snapshot/SnapshotParser, TerminalInfo, TextSanitizer, ToolArguments,
  TranscriptReconciler/TranscriptStore, WorkTimeline, Workspace.
- [x] Network: ApiException, AppExecutors, AttachmentPreparer, HttpApi,
  HttpClients, MediaLoader, PiClient. Preserve HTTPS/token confinement and the
  existing WSS lifecycle/command/history contracts.
- [x] Store/media: ConversationCache, SecureTokenStore, SettingsStore,
  Attachment, ImagePreparer. Existing Kotlin pending-record readers remain.
- [x] App shell: PiApp and MainActivity. Feature-owned catalog state/Compose
  drawer, workspace/session controls, history and grouped settings.
- [x] Catalog/history/usage: replace CatalogAdapter, ArchiveSheet and UsageCards
  with Kotlin state/Compose; retain confirmations, debounced search, pagination,
  invalidation, honest unavailable/stale usage and lifecycle polling.
- [x] Chat interop: EffortPopup, ModelSettingsSheet/TierToggle, MarkdownPreview,
  MarkdownRenderer, ImageViewer, StatusUi to Kotlin; Compose screen controls and
  narrow necessary native rendering/zoom leaves only.
- [x] Voice/updater: DictationRecorder/VoiceWaveform/AppUpdates to Kotlin and
  Compose containers; preserve real RMS, cancel/finish, private files, bounds,
  signed-upgrade verification and no automatic prompt.
- [x] UI foundation: BubbleColors, ExpressiveMotion, FadeTextView, SwipeAction,
  SystemInsets to Kotlin or remove after real callers migrate. Remove dead XML.
- [x] Update ownership/passports/maps and fix all actual compilation errors with
  limited resources. No duplicate Java/Kotlin implementations or hidden Views.
- [x] After the full rewrite: fix migrated test interactions, then integrated
  Node/architecture/baseline + JVM/build + API35 UI + font/theme/motion matrix +
  read-only HTTPS/WSS; hardware-GPU emulator, short sessions, stop afterward.
- [x] Inspect before/after visuals and record device/upgrade limitations.
- [x] Commit/publish0.6.004 and verify GitHub/Telegram delivery.
- [ ] Physical-device acceptance for the full goal; phone absent. Bounded host
  ownership is checked; do not claim physical parity from emulator evidence.

## Current evidence

Main chat/Orchestration and outbox are Kotlin/Compose. New core controller's 12
JVM cases passed (164 total); Node 78/architecture/10 pinned references passed.
First refined UI run was interrupted due to host load and has outstanding UI
assertions: Compose merged icon descriptions, Working visibility and offscreen
tool-list lookup. These are not passing gates. Brain pointer/cancel/disabled,
range and hardware-key cases passed; popup owner crash was fixed.
Temporary material: /Users/billy/temp/pi-mobile-chat-compose-20261006.

### Source checkpoint, 6 October (full verification pending)

All core/net/store/media and PiApp are Kotlin. ChatSession and Compose main chat
are written; Compose model/tier sheets, anchored effort panel, Markdown preview,
real-RMS dictation, bubble picker and Kotlin native Markdown/zoom leaves are now
in place. Existing encrypted preferences, token alias, cache schema and public
Java call signatures are retained. The small ComposeSheet host publishes activity
Lifecycle/SavedState owners before attachment and disposes detached compositions.
Core/network and UI-leaf batches passed production Kotlin/Javac compilation with
2 workers and 1 GiB heap. These checks are compilation only, not UI acceptance.

Latest live source inventory: 73 Kotlin production files / 4,572 lines, 7 Java
files / 836 lines; Kotlin is 84.5% of Java+Kotlin source lines. The exact remaining
Java files are MainActivity, ArchiveSheet, CatalogAdapter, UsageCards, AppUpdates,
FadeTextView, SwipeAction. Remove the final adapter/gesture/fade classes after
real catalog/history callers migrate, rather than keeping duplicate UI.

Bitmap import now recycles scaled images even on a successful early return and
reports actual encoded JPEG MIME after compressing WebP. Media fetch waiters are
registered atomically. PiClient checks socket identity after posting callbacks
to main and foreign-session ACK cannot consume another request's timer. These
behavior changes still require the final integrated verification. Emulator remains
off; no Node/JVM/UI suites were rerun during this rewrite checkpoint.

### Full source rewrite checkpoint

All production Android Java is removed: 81 Kotlin files, 5,463 lines at the
production compilation checkpoint. CatalogSession / CatalogScreen now own the
real catalog, drawer/grouped settings and confirmed scoped actions; MainActivity
is a thin composition/lifecycle shell. History/Usage/updater are Kotlin/Compose.
Unreachable CatalogAdapter, FadeTextView and five legacy layouts were removed;
SwipeAction is a reusable Compose action surface whose caller still confirms.
The remaining layout/icon_button.xml is the image-viewer close-button interop.
Full production compile succeeded (compile-all-android-kotlin.txt). This is not
UI/upgrade acceptance. Node suites have two process workers; Gradle has two
workers / 1 GiB heap; emulator remains off during source/test adaptation.

Synthetic tests now cancel asynchronous real-cache restore before installing the
credential-free owner: late cache callbacks previously overwrote synthetic
timelines during Working/tool-group checks. New catalog owner tests cover
confirmation, foreign ACK, stopped refresh, scoped launch, uncertain results
and unbridged terminals. Existing UI cases now address real Compose controls.
Shared font-measured skeleton text/header preserves the loaded-row geometry;
usage summary columns share the tallest measured height. New icon descriptions
are published on the actual clickable semantics node. Encryption failure on
write now reports an error and preserves the previous encrypted preference.

Next: one integrated Node/architecture/hash + JVM/build gate, then short API35
UI/matrix/HTTPS sessions on hardware GPU, visual inspection, resource samples
and stop. Required physical-device/upgrade acceptance remains outstanding.

### Integrated source/build gate

`npm run quality` completed: 78 Node cases, ownership/direction/unique source
checks, all ten pinned baseline hashes. Integrated Gradle gate
`testDebugUnitTest assembleDebug assembleDebugAndroidTest --max-workers=2`
completed BUILD SUCCESSFUL. Logs: integrated-node-quality.txt and
integrated-kotlin-build.txt in the task temporary directory. Nullable-message
assertions in tests were made explicit after core migration; no cases removed.
No full Node/unit suites were run during source rewrite; the integrated tests
ran after all production Java had moved. Emulator is still off at this checkpoint.

UI acceptance is not complete. Next inspect the drawer from the main chat as
well as catalog navigation (preserve the unsent composer while opening menus),
then API35 gestures, font/theme/motion matrix, screenshots, low-load GPU samples
and read-only relay smoke. Do not confuse Kotlin 100% with overall goal completion.

### Shared navigation build checkpoint

PiNavigation now presents the same real-catalog drawer in both catalog and chat.
Opening/closing the chat drawer retains the existing ChatSession and composer;
settings/history navigation is delegated to the activity. Connected callbacks
no longer immediately close a deliberately opened settings screen. The exact
production count is now 82 Kotlin files / 5,527 lines, Java zero.

Focused CatalogSession (7) and ChatSession (12) JVM cases passed; app/test APKs
assembled successfully with two workers (navigation-build.txt). Ownership and
diff checks passed. Two synthetic NavigationUiTest scenarios are compiled but
not yet executed. No full Node/JVM suite was repeated for this navigation change.
Emulator is still stopped; UI, display matrix and physical upgrade remain pending.

### Release authorization, 6 October

Billy explicitly requested the final visual checks, the smallest version bump,
APK delivery to Парилка228 and the GitHub release update. This supersedes the
earlier migration publication hold. Prepare 0.6.004 / versionCode 11, preserve
certificate/data, verify final visuals and upgrade, then publish and read back
the actual GitHub assets and Telegram document. Do not restart Pi/gateway.

The full API35 pass exercised 56 synthetic cases (relay skipped), with six
failures caused by reading asynchronous UI/geometry too early. Bounded waits
for actual labels, settled bounds/scroll and fixture window transitions fixed
them; all six passed in ui-failures-fixed.txt. No full suite rerun. Both shared
drawer scenarios passed and preserve text/caret. Visual review corrected an
oversized one-model selector and clipped the swipe container to remove red
corners before a gesture; the three affected UI cases passed. Narrow/dark
1.3x ten cases passed; light/2x nine passed, Usage horizontal navigation still
being checked. An attempted test swipe triggered Android's edge Back gesture;
its start now stays 48dp inside the screen. This was test input, not a layout
regression. Hardware GPU is verified Apple M5 Pro/Metal; samples during UI
were 25-100 percent CPU, with total host emulator memory around 5.4 GiB.

### Final local verification, 7 October

All56 synthetic API35 scenarios have passing coverage across the integrated50
and focused6 fixes. Narrow1.3x/2x Usage now passes with real safe-edge gestures;
large-font connection actions stack without broken words. Reduced-motion drag
passed after realistic frame-paced finger input. Final microphone/header/light
settings passed after explicit permission guard and API27-qualified theme fixes.
Lint is fail-closed and reports0 errors (145 warnings/3 information retained).
Final actual Android HTTPS/WSS passed. The exact signed APK upgrade/rollback
retains private settings/outbox and1 cache file and connects with the saved
Keystore token in both versions. Hardware emulator is stopped. See
[release0.6.004 evidence](../../docs/release-0.6.004.md). Physical phone is absent;
release is separately authorized, whole-goal acceptance is still open.

### Publication and delivery

Commit8f08a5a and tagv0.6.004 pushed. Stable latest GitHub release contains the
verified APK and SHA256SUMS.txt; API digest and downloaded bytes both match the
installed artifact. APK sent once to the confirmed Парилка228 Zoo Prison chat;
Telegram document filename/size read back. The connector temporarily received
only the requested artifact directory through supported CLI Roots; its exact
original LaunchAgent was restored and get_me/Saved Messages checks pass. No
Pi/Orca/gateway service was changed. Physical-device acceptance remains open.
