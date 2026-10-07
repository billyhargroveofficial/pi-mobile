# Catalog owner

`CatalogSession` owns the Orca catalog, connection-form state, confirmed launch /
close / delete commands, scoped results and lifecycle generations. `CatalogScreen`
owns Compose catalog, directly reachable history and grouped settings. The left
drawer is removed in0.6.005. Connection errors render below the header; updater
status lives in its settings row, not a bottom notification. `MainActivity` only
composes transport, private preferences, navigation, lifecycle and platform timers.

Public boundary: injected `CatalogSession.Transport`; listener frames from PiClient;
OpenChat/OpenHistory/Usage effects to the shell. New/close/resume require an explicit
confirmation. History owns its permanent-delete confirmation before calling
`deleteConfirmed`. Foreign-session ACK cannot consume an action, stopped refresh
cannot overwrite a new lifecycle, and unknown launch results never auto-retry.
Bare terminals remain unbridged rather than opening as controllable Pi sessions.

`ui/ArchiveSheet` owns debounced search, generation invalidation and pagination;
`ui/UsageCards` owns lifecycle polling and reported-window summaries. Both render
Compose. Marking a fixture connected does not authorize a real command: synthetic
UI tests bind a credential-free owner to the same production screen.

Checks: CatalogSessionTest, SessionGroupingTest, CatalogParserTest, ExpressiveUiTest,
DesignPreviewTest, and Node archive/usage tests. Keep screen/font/IME/theme, source
ownership and private-data upgrade acceptance in the full migration gate.
