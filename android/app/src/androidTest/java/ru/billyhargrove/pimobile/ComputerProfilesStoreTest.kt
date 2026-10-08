package ru.billyhargrove.pimobile

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.net.*
import ru.billyhargrove.pimobile.store.*
import java.util.UUID

/** Isolated preferences + actual AndroidKeyStore; synthetic sockets never reach a server. */
@RunWith(AndroidJUnit4::class)
class ComputerProfilesStoreTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun isolated(action: (SettingsStore, android.content.SharedPreferences) -> Unit) {
        val name = "profiles-test-${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try { action(SettingsStore(prefs, SecureTokenStore()), prefs) }
        finally { context.deleteSharedPreferences(name) }
    }
    @Test fun realKeystoreSavesIndependentSecretsAcrossRestartAndForget() = isolated { store, prefs ->
        val a = store.saveComputer("a.example", "Desktop", "synthetic-secret-a")
        val b = store.saveComputer("b.example:8443/gw", "Laptop", "synthetic-secret-b")
        val stored = prefs.all.values.joinToString()
        assertFalse(stored.contains("synthetic-secret-a")); assertFalse(stored.contains("synthetic-secret-b"))
        val restarted = SettingsStore(prefs, SecureTokenStore())
        assertEquals(b.id, restarted.activeProfileId()); assertEquals("synthetic-secret-b", restarted.token())
        restarted.selectComputer(a.id); assertEquals("synthetic-secret-a", restarted.token())
        restarted.forgetComputer(a.id); assertFalse(restarted.hasConnection()); assertEquals("", restarted.token())
        restarted.selectComputer(b.id); assertEquals("synthetic-secret-b", restarted.token())
    }
    @Test fun clearingOneTokenDoesNotResetSharedKeystoreKey() = isolated { store, _ ->
        val a = store.saveComputer("a.example", "A", "synthetic-a")
        store.saveComputer("b.example", "B", "synthetic-b"); store.clearToken()
        assertEquals("", store.token()); store.selectComputer(a.id); assertEquals("synthetic-a", store.token())
    }
    @Test fun legacyUpgradeKeepsItsExactEnvelopeAndAddressWithoutEagerWrites() = isolated { store, prefs ->
        val envelope = SecureTokenStore().encrypt("synthetic-legacy")!!
        prefs.edit().putString("base_url", "https://A.example:443/").putString("token_gcm", envelope).commit()
        assertEquals("synthetic-legacy", store.token()); assertFalse(prefs.contains("connection_profiles_v1"))
        val saved = store.saveComputer("a.example", "Desktop", "")
        assertEquals("legacy", saved.id); assertEquals(envelope, prefs.getString("token_gcm", null))
        assertEquals("https://A.example:443/", store.baseUrl())
    }
    @Test fun opaqueScopeIsStableOnRenameAndDifferentForHostOrToken() = isolated { store, _ ->
        val a = store.saveComputer("a.example", "A", "synthetic-a"); val first = store.connectionScope()
        store.saveComputer("a.example/", "Renamed", ""); assertEquals(first, store.connectionScope())
        store.saveComputer("b.example", "B", "synthetic-b"); assertNotEquals(first, store.connectionScope())
        store.selectComputer(a.id); assertEquals(first, store.connectionScope())
        store.saveComputer("a.example", "A", "rotated-a"); assertNotEquals(first, store.connectionScope())
        assertFalse(store.connectionScope().contains("rotated-a"))
    }
    @Test fun corruptCipherDoesNotDamageTheOtherComputerOrBorrowItsToken() = isolated { store, prefs ->
        val a = store.saveComputer("a.example", "A", "synthetic-a")
        val b = store.saveComputer("b.example", "B", "synthetic-b")
        val json = JSONObject(prefs.getString("connection_profiles_v1", null)!!)
        json.getJSONArray("computers").getJSONObject(0).put("token_gcm", "not-a-valid-envelope")
        prefs.edit().putString("connection_profiles_v1", json.toString()).commit()
        try { store.selectComputer(a.id); fail("Unavailable token must reject selection") } catch (_: IllegalStateException) {}
        assertEquals(b.id, store.activeProfileId()); assertEquals("synthetic-b", store.token())
        assertFalse(store.profiles().first().hasToken)
    }
    @Test fun identicalSessionIdsOnDifferentComputersHaveIndependentSavedReceipts() {
        val suffix = UUID.randomUUID().toString()
        val a = PendingMessages(context, "https://a-$suffix.example", "same-session")
        val b = PendingMessages(context, "https://b-$suffix.example", "same-session")
        try {
            a.save(listOf(ChatMessage.local("a-request", "Desktop receipt", null, ChatMessage.LocalState.UNCERTAIN)))
            assertTrue(b.load().isEmpty())
            b.save(listOf(ChatMessage.local("b-request", "Laptop receipt", null, ChatMessage.LocalState.ACCEPTED)))
            assertEquals("Desktop receipt", a.load().single().text()); assertEquals("Laptop receipt", b.load().single().text())
            a.save(emptyList()); assertEquals("Laptop receipt", b.load().single().text())
        } finally { a.save(emptyList()); b.save(emptyList()) }
    }
    @Test fun staleChatAndInspectionIntentsFinishBeforeSubscribingToAnotherComputer() {
        main { PiApp.get(context).client().disconnect() }
        val chat = ChatActivity.intent(context, "same-session", "Old computer", false).putExtra("connection_scope", "stale-computer-scope")
        ActivityScenario.launch<ChatActivity>(chat).use { assertEquals(Lifecycle.State.DESTROYED, it.state) }
        val inspection = OrchestrationActivity.intent(context, "same-session", "workflow", "w", "Old computer").putExtra("connection_scope", "stale-computer-scope")
        ActivityScenario.launch<OrchestrationActivity>(inspection).use { assertEquals(Lifecycle.State.DESTROYED, it.state) }
        assertTrue(PiApp.get(context).client().pendingRequestIds().isEmpty())
    }
    @Test fun replacingActualClientDropsCatalogSubscriptionSnapshotAndUncertainRequests() {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("Synthetic offline").body("".toResponseBody()).build()
        }.build()
        val client = PiClient(http, HttpApi(http)); val uncertain = mutableListOf<String>()
        val handle = PiClient::class.java.getDeclaredMethod("handleFrame", String::class.java).apply { isAccessible = true }
        val register = PiClient::class.java.getDeclaredMethod("registerPending", String::class.java, String::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        val socketField = PiClient::class.java.getDeclaredField("socket").apply { isAccessible = true }
        lateinit var old: WebSocket
        main {
            client.setListener(object : PiClient.Listener {
                override fun onConnectionState(state: ConnectionState, detail: String) {}
                override fun onCatalog(catalog: Catalog) {}
                override fun onSnapshot(snapshot: Snapshot) {}
                override fun onAck(ack: Ack) {}
                override fun onCommandUncertain(requestId: String, sessionId: String, reason: String) { uncertain.add(requestId) }
            })
            client.connect("https://a.example", "synthetic-a"); old = socketField.get(client) as WebSocket
            client.subscribe("same-session")
            handle.invoke(client, JSONObject().put("type", "catalog").put("sessions", JSONArray().put(JSONObject().put("id", "same-session").put("title", "Old computer"))).toString())
            register.invoke(client, "old-request", "same-session", true)
            val scope = client.connectionScope(); client.connect("https://b.example", "synthetic-b")
            assertNotEquals(scope, client.connectionScope()); assertTrue(client.catalog().sessions().isEmpty()); assertNull(client.lastSnapshot())
            assertEquals(listOf("old-request"), uncertain); assertTrue(client.pendingRequestIds().isEmpty())
            val desired = PiClient::class.java.getDeclaredField("desiredSessionId").apply { isAccessible = true }
            assertNull(desired.get(client))
            client.onMessage(old, JSONObject().put("type", "catalog").put("sessions", JSONArray().put(JSONObject().put("id", "late").put("title", "Late old computer"))).toString())
        }
        instrumentation.waitForIdleSync()
        main { assertTrue(client.catalog().sessions().isEmpty()); client.shutdown() }
        http.dispatcher.executorService.shutdown()
    }
}
