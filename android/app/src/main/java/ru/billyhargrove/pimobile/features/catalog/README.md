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

`ArchiveSession` owns debounced search, request identity, pagination, pending delete
results and the explicit permanent-delete confirmation. `ArchiveProjection` copies
the wire rows, validates advancing bounded cursors and builds unique stable date
groups, even when a date occurs in separate runs. `ArchiveScreen` renders these
entries. `ui/ArchiveSheet` only adapts the Android modal, I/O, callbacks and timer.
Successful deletion refreshes the current query; a retired search/deletion failure
cannot overwrite another query, and pending deletion cannot be submitted twice.

`UsageSession` owns credential-scoped single-flight polling and detail selection;
`UsageProjection` copies all finite reported quotas, their labels and freshness
once per accepted snapshot. `UsageScreen` renders immutable values, preserving
unknown usage versus reported zero. Opening details does not scan JSON again.
`ui/UsageCards` is the Android HTTP/timer adapter and keeps the existing preview API.
Both adapters retain their existing callers. State accepts only injected callbacks
on the main thread; projections have no platform or Compose dependency, and the
source guard rejects transport calls or reversed state/screen dependencies.

Marking a fixture connected does not authorize a real command: synthetic UI tests
bind a credential-free owner to the same production screen.

Checks: CatalogSessionTest, UsageSessionTest, ArchiveSessionTest, UsageProjectionTest,
ArchiveProjectionTest, CatalogDataUiTest, SessionGroupingTest, CatalogParserTest,
ExpressiveUiTest, DesignPreviewTest, and Node archive/usage tests. Keep screen/font/IME/theme, source
ownership and private-data upgrade acceptance in the full migration gate.
