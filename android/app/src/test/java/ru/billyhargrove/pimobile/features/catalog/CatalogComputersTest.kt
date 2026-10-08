package ru.billyhargrove.pimobile.features.catalog

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import ru.billyhargrove.pimobile.core.*

class CatalogComputersTest {
    private class Transport : CatalogSession.Transport {
        var entries = listOf(ConnectionProfile("a", "Desktop", "https://a.example", true), ConnectionProfile("b", "Laptop", "https://b.example", true))
        var active: String? = "a"; var connects = mutableListOf<String>(); var disconnects = 0; var commands = 0
        var reject = false; var refreshed: ((Result<Catalog>) -> Unit)? = null; var checked: ((Result<String>) -> Unit)? = null
        var timer: (() -> Unit)? = null
        override fun computers() = entries
        override fun activeComputerId() = active
        override fun catalog() = Catalog(null, listOf(Session("same-id", "Old computer", "/a", "w", "t", true, SessionStatus.IDLE, "p/m")), null)
        override fun hasToken() = active != null
        override fun hasTokenFor(url: String) = try { entries.any { it.baseUrl == EndpointPolicy.normalize(url) && it.hasToken } } catch (_: Exception) { false }
        override fun saveComputer(url: String, name: String, token: String): ConnectionProfile {
            if (reject) error("Could not save computer")
            val prior = entries.find { it.baseUrl == EndpointPolicy.normalize(url) }
            val saved = ConnectionProfile(prior?.id ?: "new", name.ifEmpty { prior?.name ?: "New computer" }, EndpointPolicy.normalize(url), true)
            entries = entries.filterNot { it.id == saved.id } + saved; active = saved.id; return saved
        }
        override fun selectComputer(id: String): ConnectionProfile { if (reject) error("Token unavailable"); active = id; return entries.first { it.id == id } }
        override fun forgetComputer(id: String) { entries = entries.filterNot { it.id == id }; if (active == id) active = null }
        override fun connect(url: String, typedToken: String) { connects.add(url) }
        override fun disconnect() { disconnects++ }
        override fun health(url: String, result: (Result<String>) -> Unit) { checked = result }
        override fun refresh(result: (Result<Catalog>) -> Unit) { refreshed = result }
        override fun command(session: String, kind: String, args: JSONObject): String { commands++; return "r-$commands" }
        override fun timeout(delay: Long, action: () -> Unit) { timer = action }
        override fun cancelTimeout() { timer = null }
    }
    @Test fun openingPickerOrEditingAnotherComputerDoesNotSwitchOrSendAnything() {
        val port = Transport(); val owner = CatalogSession(port, "https://a.example", true) {}
        owner.onConnectionState(ConnectionState.CONNECTED, "https://a.example")
        owner.showComputers(); assertTrue(owner.computerPicker); owner.editComputer("b")
        assertEquals("Laptop", owner.computerName); assertEquals("https://b.example", owner.url); assertEquals("", owner.typedToken)
        assertEquals("a.example", owner.hostLabel()); assertEquals("a", port.active); assertTrue(port.connects.isEmpty()); assertEquals(0, port.commands)
        owner.addComputer(); assertEquals("", owner.url); assertEquals("", owner.computerName); assertFalse(owner.hasStoredToken)
    }
    @Test fun blankTokenAtUnknownEndpointIsRejectedWithoutRetiringTheCatalog() {
        val port = Transport(); val owner = CatalogSession(port, "https://a.example", true) {}
        owner.addComputer(); owner.url = "new.example"; owner.connect()
        assertTrue(owner.messageError); assertFalse(owner.rows.isEmpty()); assertTrue(port.connects.isEmpty()); assertEquals("a", port.active)
        owner.typedToken = "new-secret"; owner.connect()
        assertEquals(listOf("https://new.example"), port.connects); assertEquals("", owner.typedToken); assertTrue(owner.rows.isEmpty())
    }
    @Test fun sameConnectedComputerIsANoopAndSwitchClearsOldCatalogAndConfirmation() {
        val port = Transport(); val effects = mutableListOf<CatalogSession.Effect>(); val owner = CatalogSession(port, "https://a.example", true, effects::add)
        owner.start(); owner.onConnectionState(ConnectionState.CONNECTED, "https://a.example")
        owner.switchComputer("a"); assertTrue(port.connects.isEmpty())
        owner.requestResume("same-id", "Old"); owner.switchComputer("b"); owner.confirm()
        assertEquals(listOf("https://b.example"), port.connects); assertEquals("b.example", owner.hostLabel()); assertNull(owner.confirmation)
        assertTrue(owner.rows.isEmpty()); assertEquals(0, port.commands); assertTrue(effects.contains(CatalogSession.Effect.ComputerChanged))
    }
    @Test fun staleRefreshAndHealthCannotOverwriteReplacementComputer() {
        val port = Transport(); val owner = CatalogSession(port, "https://a.example", true) {}; owner.start()
        owner.refresh(); owner.health(); val refresh = port.refreshed!!; val health = port.checked!!
        owner.switchComputer("b"); refresh(Result.success(port.catalog())); health(Result.success("Old host"))
        assertTrue(owner.rows.isEmpty()); assertEquals("", owner.message); assertFalse(owner.refreshBusy); assertFalse(owner.healthBusy)
    }
    @Test fun switchRetiresUncertainCommandsAndLaunchWithoutReplayOrForeignNavigation() {
        val port = Transport(); val effects = mutableListOf<CatalogSession.Effect>(); val owner = CatalogSession(port, "https://a.example", true, effects::add); owner.start()
        var failure = ""; var succeeded = false
        owner.deleteConfirmed("s", { succeeded = true }, { failure = it })
        owner.requestResume("same-id", "Old"); owner.confirm(); val oldTimer = port.timer!!
        owner.switchComputer("b"); oldTimer(); owner.onAck(Ack("r-1", "s", true, "")); owner.onCatalog(port.catalog())
        assertFalse(succeeded); assertTrue(failure.contains("previous computer")); assertFalse(owner.launching)
        assertEquals(2, port.commands); assertFalse(effects.any { it is CatalogSession.Effect.OpenChat })
    }
    @Test fun failedSaveOrSelectionLeavesTheWorkingComputerAndCatalogAlone() {
        val port = Transport(); val effects = mutableListOf<CatalogSession.Effect>(); val owner = CatalogSession(port, "https://a.example", true, effects::add)
        port.reject = true; owner.switchComputer("b"); assertTrue(owner.messageError); assertFalse(owner.rows.isEmpty())
        owner.url = "new.example"; owner.typedToken = "typed"; owner.connect()
        assertTrue(owner.messageError); assertEquals("a.example", owner.hostLabel()); assertFalse(owner.rows.isEmpty()); assertEquals("a", port.active)
        assertTrue(port.connects.isEmpty()); assertTrue(effects.isEmpty())
    }
    @Test fun forgettingIsConfirmedLocalAndNeverImplicitlySelectsAnotherComputer() {
        val port = Transport(); val owner = CatalogSession(port, "https://a.example", true) {}
        owner.requestForgetComputer("b"); owner.cancelConfirmation(); owner.confirm(); assertEquals(2, port.entries.size)
        owner.requestForgetComputer("a"); owner.confirm()
        assertNull(port.active); assertEquals(1, port.disconnects); assertEquals(1, port.entries.size)
        assertEquals("", owner.connectionEndpoint); assertTrue(owner.rows.isEmpty()); assertTrue(port.connects.isEmpty()); assertEquals(0, port.commands)
        owner.stop(); owner.start(); assertTrue("Returning must not resurrect the forgotten client's stale catalog", owner.rows.isEmpty())
        val recreated = CatalogSession(port, "https://a.example", false) {}; assertTrue(recreated.rows.isEmpty())
        owner.switchComputer("b"); assertEquals(listOf("https://b.example"), port.connects)
    }
}
