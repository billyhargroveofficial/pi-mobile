# Pi Mobile v0.6.003 — frozen visual behavior, not a redesign brief

These 10 tracked PNGs come exclusively from synthetic API35 Android UI fixtures (`DesignPreviewTest` and `OrchestrationUiTest`), not Billy's real conversation or account data. They form the **before** reference for the Kotlin/Compose migration. Six images originated in the unchanged v0.6.001 screens and four in v0.6.003 orchestration. `manifest.json` pins source bytes and dimensions; `node tools/check-ui-baseline.mjs` checks that no image is silently replaced. Screenshots themselves are not executable goldens: status bar clock, emulator/OS rendering and text rasterization vary. Behavior and geometry must also be checked by instrumentation tests and manual before/after review.

| Screen | What must survive |
|---|---|
| `usage-redesign.png` | Single neutral panel, three equal provider columns; actual largest quota, never invented zero. Details tap. |
| `settings-redesign.png` | One filled Connect action; update and bubble-color actions separated, touch safe. |
| `tier-picker.png` | Model, effort and Standard/Fast tiers; choice sent only on Apply; requested vs confirmed honest. |
| `voice-recording.png` | Actual microphone RMS wave, elapsed time, Cancel/Finish, ten-minute bound, no automatic Pi prompt. |
| `header-single-line.png` | Connection state + actual model + effort + available tier in one horizontal row; no “Tier unavailable” clutter. |
| `attachment-thumbnail.png` | Aspect-preserved compact preview, fullscreen zoom by tap/pinch/double-tap. |
| `orchestration-overview.png` | Workflow phases and standalone agents; status and finished-count with no fabricated progress %. |
| `orchestration-agents.png` | Distinct completed/running/queued cards and actual model/effort; agent drill-down. |
| `orchestration-conversation.png` | Read-only nested transcript and tools; child navigation, older activity. |
| `overview-font130.png` | Same hierarchy and reachable controls on narrow 360dp / 1.3× font. |

**New evidence, without overwriting the reference:** build the debug and androidTest APKs, start an isolated API35 emulator (`scripts/emulator.sh start` for port 5554), then run `scripts/capture-ui-evidence.sh [emulator-5554] [output-dir]`. It installs locally, executes synthetic `DesignPreviewTest` and `OrchestrationUiTest`, requires instrumentation `OK`, pulls nine normal screenshots, runs the workflow overview once more at 945×2100 / 1.3× font, and restores the emulator's prior size/font on exit. Results go to ignored `artifacts/ui-migration/`, including instrumentation logs; compare them with this immutable directory manually. No deterministic pixel comparator is claimed. Accept layout changes only with a specific usability reason and a documented before/after. Never overwrite the v0.6.003 reference as part of a migration.

**Coverage still to add before claiming complete parity:** main catalog & history full interaction, both light/dark theme, 2× font, IME and insets, long localized labels, reconnect/error/offline, real device APK upgrade retaining settings, duplicate attachments and read-only nested agent state. Those are behavioral gates, not reasons to redesign blindly.
