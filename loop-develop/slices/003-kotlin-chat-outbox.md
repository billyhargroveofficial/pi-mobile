# Slice 003 — Kotlin ownership of chat command receipts

Active goal: [001](../current-todo/001-todo.md). Preparation for the main chat/composer Compose slice; the chat UI remains native during this bounded change.

## Acceptance and ownership

Move optimistic prompt receipts, retained drafts, manual retry/restore and echo reconciliation out of ChatActivity into the chat feature. Keep the existing socket-write/ACK distinction, file/image ordering, delivery behavior and private host/session receipt cache. Inject only the prompt sender; the feature never connects, replays or starts another Pi. Preserve the current UI while making its eventual Compose conversion consume one tested owner.

Fix the observed restore bug: an occupied composer must neither lose its current attachments nor remove the retained failed draft. Ignore foreign/unknown ACKs and stale uncertainty after a terminal ACK. Cached receipts must remain uncertain after reentry and require reattachment instead of silently retrying absent bytes.

## Checklist

- [x] Confirm the detached migration patch is already in main and that its worktree has no user edits; remove the old worktree on Billy's explicit request. Main is the only remaining worktree.
- [x] Inspect current command lifecycle, receipt cache, reconciliation, ownership and synthetic UI checks.
- [x] Implement the Kotlin chat outbox with a narrow injected sender and integrate the existing Activity.
- [x] Migrate private receipt persistence to Kotlin while preserving old records and image attachment indices.
- [x] Verify no socket write on read-only/reentry/ACK/restore, failed-write preservation, manual retry, stale/foreign ACK, echo reconciliation and non-destructive restore.
- [x] Run npm quality, JVM/build and relevant API35 UI checks without sending a real Pi prompt.
- [x] Update owners/passports/goal and capture current code counts.
- [x] Commit the verified slice locally in main.

## Full goal still required

Compose main chat/composer and media/zoom; catalog/history/Usage/settings; voice/updater; remaining Java platform models; host gateway/extension ownership; upgrade/rollback and physical-device verification. Gateway/Caddy stay available. Push, release, deployment and Telegram remain deferred by Billy.

## Evidence

Build/JVM: 152 tests, zero failures/errors/skips. npm quality: 78 Node tests, owner/import checks and all ten immutable baseline hashes passed. Focused ChatOutboxUiTest: OK (5 tests) on API35; the sender is synthetic and cannot reach a Pi. Restored-draft capture was visually inspected: original.md and Image 2 chips fit above the four composer actions, the original prompt is preserved and send remains disabled while disconnected. The first complete synthetic UI run executed 49 tests: all new outbox checks passed, with one failure in the existing offscreen tool-scroll test. Its UiObject2 center gesture could be consumed by the nested log, so the test now swipes in the outer padding gutter and asserts that the final message is reached before returning. The strengthened focused scenario passed OK (1 test). The final complete rerun passed **OK (49 tests)** (full-instrumentation-outbox-final.txt); the first failure log is retained. The returned tool-log screenshot was also visually inspected: file-12.kt remains the first visible preview after leaving the block and returning. Gateway ACK routing, receipt replay and rejection carry the correct session ID, covered in the passing Node suite. Gateway and Caddy LaunchAgents remain running; HTTPS health returned 200. Host evidence is under /Users/billy/temp/pi-mobile-transcript-20261006. The final actual Android HTTPS REST/WSS smoke passed **OK (1 test)**, including visible Connected status and an authenticated nonempty catalog. The private token input was checked absent; MainActivity was reopened for the stand. Code inventory: 7 production Kotlin files / 861 lines; 78 Java / 6,878 lines; Kotlin is 11.1% of Android Java/Kotlin lines. Total counted source/resources/tests/scripts: 242 files / 13,054 lines, including blank lines and comments, excluding dependencies/generated/build/docs/skills. No release or physical phone verification is claimed.

## Next slice

Adopt the shared Compose transcript in the main chat and migrate its header/composer. Keep ChatOutbox as the sole receipt/draft owner, preserve history anchors and follow-tail, and verify Markdown/documents, local attachment thumbnails, ACK actions, keyboard/insets and read-only behavior on the actual renderer. Native Markdown/zoom and remaining control sheets remain explicitly bounded interop until their own migration.
