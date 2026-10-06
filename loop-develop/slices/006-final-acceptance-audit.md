# 006 — Final acceptance audit

7 October 2026. Goal: [001](../current-todo/001-todo.md). Android rewrite and
authorized release are finished; the full goal still has external acceptance
requirements. Work remains directly in main. Do not repeat completed suites,
start an emulator or alter live Pi/Orca/gateway to replace missing evidence.

## Completed requirements and evidence

| Requirement | Verified result |
|---|---|
| Entire production Android migration | 82 Kotlin files / 5,537 lines, no production Java. Chat/composer, catalog/drawer, history, Usage/settings, workflows, model/effort/tier, voice/updater use Compose. Bounded native Markdown/LaTeX, zoom and modal hosts are documented; one close-button XML remains. |
| State, private data and read-only boundaries | ChatSession/ChatOutbox/CatalogSession own state and commands. Integrated JVM170 plus focused19 including a new case give171 distinct cases of coverage. Session/request/epoch, uncertain receipts, retained drafts and read-only policies have regression coverage. |
| Agent-readable architecture | Source ownership, platform dependency direction and server/extension/contracts lanes are guarded; maps describe actual code. Final audit found and closed a bypass through feature-owned files in common `ui/`. Public file-level facades and composition roots are now registered. Generic StatusUi belongs to shared UI foundation. Six focused architecture cases pass, including wildcards, aliases, secondary Kotlin exports and malformed ownership metadata. No runtime or build framework added. |
| Visual and behavior checks | Integrated API35 synthetic run:50 pass/6 failures, then all6 targeted fixes pass. Both themes,360dp,1.3×/2× font, Usage/settings, workflow/reader/tier, drawer/caret and reduced motion have passing coverage and visual review. Ten frozen before references retain their hashes. This is coverage across runs, not a claim that an unperformed full rerun was green. |
| Build/lint | Final APK and test APK build. Lint0 errors/145 warnings/3 information; discovered permission/API-level errors fixed and affected cases pass. Resource limits and teardown are recorded in [release verification](../../docs/release-0.6.004.md). |
| Actual Mac relay / cold app launch | Final Android APK passes authenticated HTTPS catalog and WSS Connected on the existing Mac relay. Retained Keystore credentials also connect after in-place upgrade and rollback launches. Gateway/Pi were not restarted. |
| Upgrade and rollback | Exact final signed APK0.6.004→0.6.003→0.6.004 on API35; private settings/outbox and one cache file retain their hashes. Package, certificate, versionCode11 and APK digest verified. |
| Authorized publication and delivery | Stable latest[v0.6.004](https://github.com/billyhargroveofficial/pi-mobile/releases/tag/v0.6.004), source8f08a5a. GitHub asset/download/SHA256SUMS agree. Telegram document304909 in confirmed Парилка228 Zoo Prison has the expected filename/17135448-byte size. No new APK is needed for the later architecture-tool change. |

## Linux read-only probe after release

Verified SSH target `brother`: host/user `flyingkuskus`, home
`/home/flyingkuskus`, runtime `/home/flyingkuskus/.local/share/pi-mobile`.
Existing pi-mobile.service is active but runs gateway0.5.1; it was not deployed
or restarted. Public HTTPS `/pi-mobile/health` returns200; catalog without token
returns401, with a browser Origin403, and with bearer200. Two fresh WSS
connections receive catalog frames, taking718ms and585ms. Service PID is
unchanged. Token stays in remote process memory and is not logged.

This proves Linux transport/authentication and fresh socket reconnection, not
new Android UI/session parity. Both catalogs are empty. Local gateway inspection
reports inventory unavailable and the configured Orca runtime file is absent.
Do not infer that a real Pi chat or new gateway deployment passed on Linux.

## Remaining acceptance

- [ ] Physical Android: in-place installation with retained data, installer
  confirmation, IME/attachments/gallery and microphone on the actual phone.
- [ ] Actual client recovery after network loss/background return, including
  cached transcript/anchor and no prompt replay. Existing cold launches, unit
  policies and host socket probes do not by themselves prove this journey.
- [ ] Real session on the Linux relay when Orca inventory is available; check
  the current APK against that host without controlling or reloading Pi.

No Android device is connected. A question about USB versus manual phone
acceptance is pending. These missing environments prevent full acceptance;
the APK release/delivery are already complete.

## Local evidence

`/Users/billy/temp/pi-mobile-chat-compose-20261006/`: release evidence named in
docs/release-0.6.004.md, architecture-owner-audit.txt,
linux-runtime-inspection.json, linux-relay-readonly.json and
linux-inventory-inspection.json. Synthetic fixtures do not send paid prompts;
Linux probe sends no Pi commands. Production Android/APK remain unchanged by
this post-release audit.
