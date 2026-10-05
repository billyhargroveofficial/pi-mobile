# Verification — 0.4.0 (2026-10-05)

## Automated

- Node tests:20/20 passing. New coverage: visible Orca layout membership (including inactive panes), orphan/background exclusion, authenticated archive paging, no launch-path disclosure, saved-header validation, single-flight/durable resume, unknown-launch fencing, delayed catalog appearance, switched sessions, closed-tab and old-session removal, WS repeated resume.
- Android unit tests:102/102 passing.
- Android instrumentation:10/10 at420dpi/font1/light, and10/10 at480dpi/font1.3/dark. Restored normal configuration. Includes a visible48dp separate History action.
- Java17 debug/app-test builds successful.

## Real public HTTPS + emulator + Orca

Used only a pre-existing dedicated8-message `Pi Mobile UI verification` fixture, session `01a10bfd-944a-71ca-adaa-ccb821342665`; no prompts sent and no user agent stopped/reloaded.

1. Main catalog matched five open Pi tabs on Mac; closed gateway registrations absent.
2. Emulator: History → fixture → confirmation → Open. Gateway created visible Orca tab `term_0d06ebdb-c916-4445-937e-c773b94f1d8d`; phone automatically opened the same connected session with previous messages.
3. Two additional distinct resume request IDs returned `reused:true` and the same terminal handle; terminal count stayed six. Opened fixture absent from archive.
4. Closed only the owned fixture terminal (PTY kill confirmed). Main catalog returned to five, matching actual Mac terminal identities exactly. Fixture returned to History with the same8 messages.
5. Screenshot of final open-only catalog: local ignored `artifacts/v4-open-tabs.png`.

During integration, Orca's create-with-`--focus` path timed out before producing a terminal. The final implementation uses ordinary `terminal create`, which was verified to create a visible tab without that flag. Unknown results remain fenced rather than retried automatically. Test-only failed intents were reconciled manually after inspecting terminal/process inventory; other user terminals were untouched.

## Limits

History covers known local Orca workspaces and Pi's standard session storage, not arbitrary custom session directories or remote hosts. Unknown unbridged Pi in a workspace prevents archive resume there until the idle Pi reloads the bridge. Startup dialogs may still require Mac interaction. Inventory refresh is approximately3 seconds; failed inventory reads retain explicitly marked last-known data. Debug prerelease, no independent security audit.
