# Design pass — 0.6.001

## Applied skills

Text-only, MIT-licensed copies in `.agents/skills/` from `krutikJain/android-agent-skills`, pinned commit `c5bf6731b8441019418784484cca1578413e6ad3`:

- `android-mobile-frontend-design` (+ patterns/scenarios)
- `android-viewsystem-foundations`
- `android-material3-design-system`

Source: https://github.com/krutikJain/android-agent-skills/tree/c5bf6731b8441019418784484cca1578413e6ad3/skills

Licenses are included beside the skills. No upstream installer or scripts were executed. Upstream example commands refer to that repository, not this app. Keep this app native Java/Views; skill suggestions are guidance, not permission to migrate stacks.

Additional Sol research checked ComposioHQ/awesome-claude-skills at `be2a406907dbc61b73e6827ded415c96139d13a2`; transferable review methods were found in `wholiver/swiftui-design-skill` at `2c82638ebd3c801d9d2d12b5f2d6c20495939995` and `uxKero/anydesign` at `d81bd8957a21c2d7fba8d2a8f4f60050d09368b5`. Neither their code nor their frameworks are used.

## Decisions

Mode: improve usage, fix settings overflow/competing actions, create focused recording state.

- Confident utility: black base, neutral surfaces, one primary action per sheet, 4dp spacing rhythm.
- Usage is one three-column summary rather than three uneven stacks. Show the most-used actual window, clearly label which window it is, and retain every window/reset timestamp in a detail sheet. Missing data is a dash, not zero. Large accessibility text switches to horizontally scrollable wider columns rather than clipping.
- Settings retain Connect as the sole filled primary action. App updates and bubble color are separated, secondary list rows. Insets and keyboard resizing remain explicit.
- Recording has a real elapsed timer and microphone RMS waveform, with clear Cancel / Finish. Audio streams into a private temporary PCM file, not an ever-growing Java byte array. Limit: 10 minutes. Binary upload avoids base64 expansion; native transcription runs bounded chunks up to30 seconds with quiet-boundary preference.
- Model/effort/tier displayed at the top distinguish active request settings from next-request selections. The tier control sends a real session-local provider-payload override; a requested Fast tier is not reported as provider-confirmed until an actual stream event reports it.

## Verification notes

Emulator screenshots belong in ignored `artifacts/design-0.6.001/`. Use synthetic session data; never send prompts or stop existing Pi processes for UI tests. The microphone test creates and deletes a private PCM file but never sends a Pi prompt. A75-second locally synthesized recording was successfully transcribed by the actual installed sherpa-onnx/Parakeet engine.

Verified on the API35 emulator: all 35 instrumentation tests pass, including four new design/recording/header tests. Node: 53 passing; JVM: 127 passing. Latest usage, settings, model picker and chat-header screenshots were inspected. The tier picker also passes and remains readable at 360dp width with 1.3× font scale. Large-font usage scrolling and keyboard-open settings reachability have not yet received equivalent visual verification. These screenshots use fixtures, not real account quotas.

Live provider acceptance of priority depends on provider/account availability; a mocked transport test is not evidence of account entitlement. Existing Pi sessions need an idle `/reload` before advertising tier controls. Never reload a running user session automatically.
