# Slice 004 — Kotlin/Compose main chat

Active goal: [001](../current-todo/001-todo.md). Previous turn made verified progress: slice 003 is committed as 239a3c8; main is clean and is the only worktree. The full migration remains active.

## Acceptance and ownership

Billy's steering on 6 October: speed up migration and use the six supplied
ChatGPT Android screenshots as the new visual reference, while migrating to
Kotlin/Compose. This explicitly authorizes replacing the old visual hierarchy:
black dark canvas, blue user bubbles, rounded composer, a history/navigation
drawer and grouped settings rows. Keep Pi functionality, honest session status,
authorization and private data. The supplied ChatGPT product items are visual
references, not permission to invent unsupported Pi features. Catalog/drawer
and grouped settings remain required in the full goal after the chat slice.

Replace the main Java/XML chat with a Kotlin shell and a feature-owned Compose
header, transcript, attachment chips, skill suggestions and composer. ChatOutbox
remains the only owner of prompt receipts and retained drafts. Feature state owns
canonical transcript, scoped ACK/control/history/configuration, and follow-tail;
the Activity owns only lifecycle composition, permissions, pickers/navigation and
temporary platform sheets. No second transcript adapter or fake legacy Views.

Preserve the neutral palette, one-row status/model/effort/tier, chronological
progress/tools, manual disclosure, persisted viewport, image aspect/zoom, draft
recovery, skill/MCP/name controls, delivery semantics and no automatic replay.
All commands keep the original read-only/session/capability guards. Markdown,
fullscreen zoom, dictation and model/effort sheets may remain explicitly bounded
platform interop pending their own slices; the chat surface/composer must be
Compose. Existing Pi/Orca and gateway/Caddy continue running. No paid prompt,
reload, push, release or publication.

## Checklist

**Latest instruction, 6 October:** Billy explicitly requested completing the
remaining code/UI rewrite first, then one integrated test pass. This supersedes
the per-slice full-test cadence. Do not start the emulator or repeat suites during
the remaining rewrite; use resource-limited compilation only. The emulator was
stopped after live top showed 779% CPU and 8.7GB from software SwiftShader/lavapipe
rendering. That interrupted synthetic run is not accepted as a passing gate.
The active rewrite inventory continues in [005](005-complete-android-rewrite.md).

Additional authorized scope: match the animated effort slider from
https://x.com/ozzyxs1a/status/2107523169649389677. Reference retrieved through an
owned background Chrome Shared tab with Playwriter; that tab/session were closed.
Downloaded 1890×1032 / 60fps / 5.55s MP4 and frame sheet are in the task temp
directory. Pink brain thumb, spring travel, tinted fill and violet particle
effect at the actual highest supported xhigh/max; no invented wire level.
Billy rejected the first visual pass as rougher than the reference. The revised
control uses a thinner dark track, rounded brain lobes with a thick dark outline,
cyan/violet/pink gradient at maximum, an irregular pixel grid denser near the
thumb and faint radial pixel trails. The oversized circular halos were removed.
The popup also shows Faster/Smarter range labels and a colored actual level.

- [x] Revalidate clean main, prior accepted outbox, current goal, ownership, baseline, protocol and design constraints.
- [x] Capture synthetic native chat/header/attachment evidence before replacement (API35 OK 2, native-before/ screenshots).
- [x] Implement one feature-owned Kotlin chat state/controller with injectable transport (12 meaningful JVM tests passed).
- [x] Extend the public transcript renderer for local receipts/thumbnails/documents while keeping inspection read-only (pending complete UI gate).
- [x] Implement Compose header, overlays, attachment/skill rows, adaptive composer and delivery picker; integrate Kotlin Activity (pending complete UI gate).
- [ ] Verify the shared Compose brain effort slider: actual finger drag, release-only commit, keyboard/range accessibility, cancellation, motion/disabled state and image/frame evidence.
- [x] Remove replaced Java chat, message adapter and unreachable chat-only Views/XML after callers migrate (8 Java widgets, 3 layouts).
- [x] Verify ACK, restore/retry, readonly, epoch/page/reconnect and canonical echo against a fake transport; preserve private settings/cache (164 JVM total).
- [ ] Migrate existing UI checks to actual Compose interactions/semantics and verify the complete synthetic API35 suite.
- [ ] Verify normal and 360dp / 1.3x dark / 2x light, IME/insets, long text, media zoom and before/after.
- [ ] Run npm quality, JVM/build, diff/ownership checks and actual read-only Android HTTPS/WSS smoke.
- [ ] Update source maps, passports/goal/evidence and commit locally in main.

## Full goal still required

Catalog/history/Usage/settings; voice/updater; remaining platform Java models;
bounded host gateway/extension ownership; physical phone, upgrade/rollback and
critical live scenarios. Completion is not established by this single screen.

## Evidence

Complete gate pending. The first focused slider run passed 7/8 cases; its one
failure was an immediate close assertion during the existing 450ms post-release
animation. That assertion now waits for actual popup disappearance. All pointer,
cancel, disabled, range-accessibility and intermediate-animation cases passed;
the additional hardware-key release test is in the final suite. PopupDecorView
now receives Activity lifecycle/saved-state owners before Compose attaches,
fixing the actual missing-owner crash. Final suite is running on the refined UI.
Temporary host materials belong under
/Users/billy/temp/pi-mobile-chat-compose-20261006.
