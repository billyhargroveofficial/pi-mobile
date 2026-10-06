# Slice 002 — Compose transcript for read-only agent inspection

Active goal: [001](../current-todo/001-todo.md). This slice does not complete the full migration.

## Acceptance and ownership

Replace the RecyclerView/MessageAdapter hosted by OrchestrationActivity with a Kotlin/Compose transcript. Keep prompt, progress, independent tool groups and final answer in their existing order. Preserve manual expansion after settlement, stable anchors when older messages prepend, and scrolling without jumping when live content changes. Inspection stays read-only; no Pi command, runtime restart or publication.

The pure transcript presentation model belongs to core. The reusable transcript UI belongs to chat and exposes a narrow rendering contract to orchestration. Markdown/LaTeX may retain an explicitly bounded native TextView rendering leaf while the list, message geometry and tools are Compose; this is not a migrated full chat screen. Existing MessageAdapter remains only for ChatActivity until that separate slice passes parity.

## Checklist

- [x] Inspect both existing worktrees; confirm the existing Compose commit is already integrated into main.
- [x] Read current goal, owners, protocol, baseline and source; read nareshka architecture principles only.
- [x] Capture existing synthetic agent transcript on API35 before changes.
- [x] Implement and verify pure presentation/settlement state shared by old and new renderers.
- [x] Implement Compose transcript and integrate read-only inspection.
- [x] Verify tool disclosure, independent progress, repeated snapshots, paging anchor and no forced scroll; restore the inner reading position after collapse and after the keyed tool block leaves the viewport.
- [x] Restore the existing gateway/Caddy LaunchAgents on Billy's explicit request; verify real Android HTTPS REST/WSS without a Pi command.
- [x] Run npm quality, JVM/build gates, full API35 UI tests and dark/narrow/large-font checks.
- [x] Compare before/after evidence and update owners, passports and goal truthfully.

## Full goal still required

Chat/composer and media/zoom; catalog/history/Usage/settings; voice/updater; remaining Java models and platform services; bounded gateway/extension ownership; device upgrade/rollback, critical live scenarios and physical-device verification. Release, push, deployment and Telegram remain deferred by Billy.

## Evidence

Baseline API35 agent transcript: OK (1 test), agent-transcript-before.png. Build/JVM: 134 tests, zero failures/errors/skips. npm quality: 78 Node tests plus ownership and immutable screenshot hash gates passed. The first full API35 run completed 44 tests: all eight Orchestration tests passed; microphone, attachment and effort-frame tests failed. The test harness now disconnects its Android client before synthetic fixtures, cleans up microphone capture on assertion failure, waits for image binding, accepts elapsed PCM beyond the exact second label and samples animation on frames instead of a 90ms thread sleep. Zoom checks pinch-in/out and the correct double-tap toggle with fixed touch timestamps; a rerun using wall-clock inter-tap delays was stopped after its intermittent zoom failure. All three failing checks passed focused reruns. The final complete synthetic suite passed **OK (44 tests)** (full-instrumentation-final.txt). An additional focused reader scenario passed: the inner log stays capped at 200dp and retains the reading position after collapse and after scrolling 50 messages away/back (compose-tools-returned.png).

Reader and workflow navigation passed **OK (2 tests)** per variant at 945×2100 / 1.3× dark and 2× light. All four matrix PNGs were visually inspected; size, font and night mode were restored. Before/after review of agent-transcript-before.png and agent-transcript-after-final.png confirmed palette, order, message geometry and tool glyphs. The tool disclosure is now 48dp, and accepted-message ticks align with the bubble's end edge. A fresh focused normal capture passed OK (1 test); an earlier capture during the long run had transient incomplete emulator text rendering and was replaced with this checked capture. No pixel-identical golden comparison is claimed.

A separately requested opt-in RelayConnectionSmokeTest passed against the actual HTTPS REST/WSS client; its private cache input was removed and the token stays in Keystore. Phone UI/upgrade is still unverified because only emulator-5554 is attached. Host screenshots/logs go exclusively under /Users/billy/temp/pi-mobile-transcript-20261006; immutable docs/ui-baseline images stay unchanged. Full migration remains active, with the next bounded slice targeting main chat/composer and optimistic ACK state.
