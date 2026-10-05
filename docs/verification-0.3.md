# Verification — 0.3.0 (2026-10-05)

## Automated

- `npm test`: 10/10 passing (routing/auth/configuration/live tools plus paged history, persisted turn identity/metrics, scoped Markdown files).
- `testDebugUnitTest`: 102/102 passing, including work grouping/collapse, history prepend/live deduplication, math delimiter normalization.
- Direct API35 ARM64 emulator instrumentation: 9/9 passing in normal light mode; 9/9 again at480dpi (360dp wide), font scale1.3, dark mode. Restored420dpi/font1/light afterwards.
- Native formula test waits for a populated drawable, not merely raw LaTeX text.
- Debug APK and instrumentation APK build successfully with Java17.

## Actual live Pi / public HTTPS

Used a dedicated `Pi Mobile UI verification` session, never another working agent. Seeded136 source messages through Pi SessionManager, then opened it in a real interactive Pi with the installed plugins.

- Initial snapshot40; three previous pages reach the oldest marker with136 unique source messages.
- Scoped document command returns the real Markdown file.
- Real harmless bash/read turn: three live tool updates, image result, final marker. Recorded17.756s duration,80 output tokens /3.709s measured generation =21.6 tokens/s. Verified persisted custom metric entry.
- Selected another available model and restored original without a prompt on the alternative model.
- Emulator: commentary interleaves tool rows; arguments fade; final response stays outside work block. Tapping work header hides tools but keeps final answer.
- Tapped a Markdown hyperlink in chat: native bottom sheet renders bold/italic/code/list/table and fraction/square-root formula. Visual screenshot: ignored local `artifacts/v3-markdown-preview.png`.
- Host package warning fix independently checked with real Pi RPC startup/get_state: no dependency warning or duplicate local Pi runtime packages.

## Scope / limitations

No independent security audit. No production signing/Play Store publishing. Gradients are implemented with frame invalidation and reduced-motion handling; no frame-rate benchmark was performed. Older turns without recorded metrics are intentionally unmeasured. Reconnect reloads the tail rather than retaining previously paged history. Nested live tool details absent from Pi's session history cannot be reconstructed after restart. Native renderer supports Markdown and a LaTeX subset, not arbitrary TeX packages.
