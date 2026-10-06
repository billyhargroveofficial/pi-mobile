package ru.billyhargrove.pimobile.features.catalog

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import ru.billyhargrove.pimobile.core.*

class CatalogSessionTest {
    @Test fun openingSettingsWhileConnectedDoesNotImmediatelyCloseIt() {
        val owner = CatalogSession(Transport(), "https://example.invalid", true) {}
        owner.screen = CatalogSession.Screen.Settings
        owner.onConnectionState(ConnectionState.CONNECTED, "")
        assertEquals(CatalogSession.Screen.Settings, owner.screen)
        owner.connect(); owner.onConnectionState(ConnectionState.CONNECTED, "")
        assertEquals(CatalogSession.Screen.Catalog, owner.screen)
    }
    private class Transport : CatalogSession.Transport {
        val writes = mutableListOf<Triple<String, String, JSONObject>>()
        var refresh: ((Result<Catalog>) -> Unit)? = null
        var timer: (() -> Unit)? = null
        var next: String? = "request"
        override fun catalog() = Catalog.empty()
        override fun hasToken() = true
        override fun connect(url: String, typedToken: String) {}
        override fun disconnect() {}
        override fun health(url: String, result: (Result<String>) -> Unit) { result(Result.success("")) }
        override fun refresh(result: (Result<Catalog>) -> Unit) { refresh = result }
        override fun command(session: String, kind: String, args: JSONObject): String? { writes += Triple(session, kind, args); return next }
        override fun timeout(delay: Long, action: () -> Unit) { timer = action }
        override fun cancelTimeout() { timer = null }
    }
    @Test fun cancelledCloseCannotReachTheHost() {
        val transport = Transport(); val owner = CatalogSession(transport, "https://example.invalid", true) {}
        val row = CatalogRow.session(Session("s", "Task", "/w", "w", "t", true, SessionStatus.RUNNING, "p/m"))
        owner.requestClose(row); assertTrue(owner.confirmation!!.message.contains("interrupted")); assertTrue(transport.writes.isEmpty())
        owner.cancelConfirmation(); owner.confirm(); assertTrue(transport.writes.isEmpty())
        owner.requestClose(row); owner.confirm()
        assertEquals("close", transport.writes.single().second); assertTrue(transport.writes.single().third.getBoolean("force"))
    }
    @Test fun foreignAckCannotConsumeDeletionCallback() {
        val transport = Transport(); val owner = CatalogSession(transport, "https://example.invalid", true) {}
        var completed = 0
        owner.deleteConfirmed("mine", { completed++ }) { fail(it) }
        owner.onAck(Ack("request", "other", true, "")); assertEquals(0, completed)
        owner.onAck(Ack("request", "mine", true, "")); owner.onAck(Ack("request", "mine", true, "")); assertEquals(1, completed)
    }
    @Test fun stoppedRefreshCannotOverwriteNextLifecycle() {
        val transport = Transport(); val owner = CatalogSession(transport, "https://example.invalid", true) {}
        owner.start(); owner.refresh(); val old = transport.refresh!!
        owner.stop(); owner.start()
        old(Result.success(Catalog(listOf(Workspace("old", "Old", "/old")), emptyList(), emptyList())))
        assertTrue(owner.rows.isEmpty()); assertFalse(owner.refreshBusy)
    }
    @Test fun launchIsScopedAndOpensOnlyAfterTheRealCatalogConnects() {
        val transport = Transport(); val effects = mutableListOf<CatalogSession.Effect>()
        val owner = CatalogSession(transport, "https://example.invalid", true, effects::add); owner.start()
        owner.requestResume("s", "Saved"); assertTrue(transport.writes.isEmpty()); owner.confirm(); assertTrue(owner.launching)
        owner.requestResume("other", "Other"); owner.confirm(); assertEquals(1, transport.writes.size)
        owner.onAck(Ack("request", "foreign", false, "wrong")); assertTrue(owner.launching)
        owner.onCatalog(Catalog(null, listOf(Session("s", "Saved", "/w", "w", "t", false, SessionStatus.OFFLINE, "p/m")), null))
        assertFalse(effects.any { it is CatalogSession.Effect.OpenChat })
        owner.onCatalog(Catalog(null, listOf(Session("s", "Saved", "/w", "w", "t", true, SessionStatus.IDLE, "p/m")), null))
        assertEquals(CatalogSession.Effect.OpenChat("s", "Saved"), effects.last()); assertFalse(owner.launching)
    }
    @Test fun uncertainLaunchNeverRetriesOrCreatesAnotherPi() {
        val transport = Transport(); val owner = CatalogSession(transport, "https://example.invalid", true) {}
        owner.start(); owner.requestResume("s", "Saved"); owner.confirm()
        owner.onCommandUncertain("request", "s", "timeout"); owner.onConnectionState(ConnectionState.CONNECTED, "")
        assertFalse(owner.launching); assertEquals(1, transport.writes.size); assertTrue(owner.message.contains("no automatic retry"))
    }
    @Test fun bareTerminalCannotOpenAsAControllableSession() {
        val effects = mutableListOf<CatalogSession.Effect>()
        val owner = CatalogSession(Transport(), "https://example.invalid", true, effects::add)
        owner.open(CatalogRow.terminal(TerminalInfo("t", "Terminal", "w", "", true)))
        assertTrue(effects.isEmpty()); assertTrue(owner.message.contains("Extension required"))
    }
}
