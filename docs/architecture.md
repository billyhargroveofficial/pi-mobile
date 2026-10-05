# Architecture map — Pi Mobile (migration in progress)

**Current release:** `v0.6.003` (`3c08e10`), Java/XML Android, Node gateway and Pi extension. **Working tree:** incremental Kotlin/Compose and capsule migration under the explicitly requested [goal](../loop-develop/current-todo/001-todo.md); no APK/release/deploy until Billy checks it. Do not confuse target architecture with already shipped code.

## Product invariants

```text
Existing Pi/Orca process ← Unix bridge ← Node gateway (127.0.0.1, bearer, HTTPS proxy)
                                             ↕ explicit authenticated wire
                          Android app (same package/signature/preferences)
```

A screen never creates a second Pi or silently resends an uncertain command. Gateway owns server-side authorization, origins, bounded media/history, update and agent-inspection scope. Extension owns Pi-session-only configuration and the Pi API; a physical device receives strictly sanitized projections, not global defaults, raw session files or credentials. Preserve scheme/versionCode/signing and upgrade-in-place. See `docs/protocol.md`, `docs/setup.md` and security tests.

## Current ownership (source of truth)

Machine-readable class ownership is [architecture/owners.json](../architecture/owners.json): app shell (`PiApp`), catalog, chat, orchestration, voice, updater and cross-feature UI. Existing platform packages are `core`, `net`, `store`, `media`. Physical class paths still reflect the Java View system; owner names are a guide for migration, not an assertion that Compose modules already exist. `node tools/check-architecture.mjs` refuses unowned shell/UI classes, duplicate Java/Kotlin implementations and reversed platform dependencies.

Node lanes: `contracts/` owns pure shared payload projection; `extension/` observes active Pi and transports bounded snapshots over a private bridge; `server/` owns loopback gateway, persistence, REST/WSS and policy. Both host runtimes may depend on `contracts/`; they must not import each other's implementation. `contracts/agent-transcript.mjs` is the first extracted shared seam; `server/command-policy.mjs` validates bounded commands/images, and `server/session-projection.mjs` isolates allowlisted configuration/page metadata and checkpointed snapshot/delta frames from the gateway event loop. No source file in `contracts/` imports either runtime. Checks are additive and fail on new violations rather than hiding debt behind a growing baseline.

## Desired dependency direction

```text
Android thin app shell → feature public APIs → core protocol/session/media/storage and UI tokens
                      no feature → another feature's implementation
Gateway composition → bounded server capabilities → pure shared contracts
Pi extension adapter → Pi lifecycle + pure shared contracts
```

Start as source-package ownership; add Gradle modules only if they buy tested isolation and independent ownership. Work per vertical user journey, not by renaming folders. The app shell composes navigation/lifecycle, features own screen state and errors, core owns stable domain-neutral primitives. Backend transport accepts only bounded versioned DTOs; unavailable provider capability remains unavailable, never inferred from a global setting. No direct Android UI import from gateway or Pi extension.

## Frozen UI baseline and migration gates

[docs/ui-baseline](ui-baseline/README.md) pins ten synthetic screenshots and lists preserved behavior. Run `npm run quality`, then JVM + Android instrumentation, compare before/after visually and verify dark/light, narrow font scaling, IME and system insets. New Compose widgets must preserve content order, reading hierarchy and interaction geometry unless a concrete defect was reproduced and improvement demonstrated. XML Views and Compose may coexist only during a bounded vertical migration, without dual logic forever.

The **active goal is not done** when only Gradle supports Compose or one screen has moved. Completion requires all shipped screens migrated, platform-dependent interop explicitly documented, old unused Java/XML removed, canary/upgrade checks on the same signed package, safety/auth tests and reproducible UX parity. Working Pi agents must not be reloaded or stopped for verification.
