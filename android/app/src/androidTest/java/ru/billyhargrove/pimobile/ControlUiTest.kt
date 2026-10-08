package ru.billyhargrove.pimobile

import android.Manifest
import android.net.ConnectivityManager
import android.view.Choreographer
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.features.chat.ChatSession
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Offline Activity/native dialog/Compose controls. Transport records reads/stops; prompts never escape. */
@RunWith(AndroidJUnit4::class)
class ControlUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val session = "controls-synthetic"
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout = 1000
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.ACCESS_NETWORK_STATE)
        try { assertNull("Control fixtures require an offline emulator", context.getSystemService(ConnectivityManager::class.java).activeNetwork) }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000); instrumentation.uiAutomation.clearCache() }
    private fun capture(name: String) {
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync { Choreographer.getInstance().postFrameCallback { Choreographer.getInstance().postFrameCallback { done.countDown() } } }
        assertTrue(done.await(3, TimeUnit.SECONDS)); assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "controls-$name.png")))
    }
    private fun launch(readOnly: Boolean = false) = ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, session, "Control fixture", readOnly))
    private fun node(selector: BySelector) = requireNotNull(device.wait(Until.findObject(selector), 4000))
    private fun data(name: String = "filesystem") = JSONObject().put("type", "mcp").put("observedAt", 1700000000000L).put("servers", JSONArray()
        .put(JSONObject().put("name", name).put("status", "connected").put("toolCount", 4))
        .put(JSONObject().put("name", "search").put("status", "cached").put("toolCount", 2))
        .put(JSONObject().put("name", "calendar").put("status", "needs-auth").put("toolCount", 0)))
    private inner class Host(val scenario: ActivityScenario<ChatActivity>, readOnly: Boolean = false) {
        lateinit var chat: ChatSession; lateinit var wire: ChatFixture.Transport; lateinit var activity: ChatActivity
        init { scenario.onActivity { a ->
            activity = a; chat = ChatFixture.install(a, session, readOnly, emptyList())
            wire = ChatSession::class.java.getDeclaredField("transport").apply { isAccessible = true }.get(chat) as ChatFixture.Transport
            chat.onConfiguration(session, JSONObject().put("capabilities", JSONArray().put("mcp").put("name")))
        }; idle() }
        fun onMain(action: () -> Unit) { instrumentation.runOnMainSync(action); idle() }
        fun send(text: String = "/mcp"): String {
            onMain { ChatFixture.text(chat, text); assertFalse(chat.send(CommandBuilder.Behavior.FOLLOW_UP)) }; return "read-${wire.reads}"
        }
        fun reply(id: String, name: String = "filesystem", ack: Boolean = true) {
            onMain { chat.onData(id, session, data(name)); if (ack) chat.onAck(Ack(id, session, true, "")) }
        }
        fun dialog(): AlertDialog? = ChatActivity::class.java.getDeclaredField("mcpDialog").apply { isAccessible = true }.get(activity) as AlertDialog?
        fun noPrompt() { assertEquals(0, wire.prompts); assertTrue(chat.queue.isEmpty()); assertTrue(chat.items.none { it.row.message?.isLocal == true }) }
        fun notice(text: String) { node(By.res(context.packageName, "chatNotice")); node(By.text(text)) }
    }
    /** This method also runs against the previous APK as the before screenshot. */
    @Test fun mcpStatusPreservesNativeModalContentGeometryAndDone() {
        launch().use { scenario ->
            val host = Host(scenario); val id = host.send(); host.reply(id)
            node(By.text("Session MCP servers")); node(By.textContains("filesystem — connected · 4 tools")); node(By.textContains("search — cached, disconnected · 2 tools"))
            node(By.textContains("calendar — sign-in required · 0 tools")); node(By.textContains("Status reported by Pi:"))
            host.noPrompt(); assertEquals(1, host.wire.reads); assertEquals(0, host.wire.aborts); assertEquals("", host.chat.composer.text); capture("mcp")
            node(By.text("Done")).click(); assertTrue(device.wait(Until.gone(By.text("Session MCP servers")), 3000))
        }
    }
    @Test fun duplicateMcpAndWrongCommandDataCannotCreateAnotherWindow() {
        launch().use { scenario ->
            val host = Host(scenario); val id = host.send(); host.reply(id, ack = false); node(By.text("Session MCP servers"))
            val shown = requireNotNull(host.dialog())
            host.onMain { host.chat.onData(id, "foreign", data("foreign")); host.chat.onData(id, session, data("duplicate")); host.chat.onAck(Ack(id, session, true, "")) }
            assertSame(shown, host.dialog()); assertFalse(shown.findViewById<android.widget.TextView>(android.R.id.message)!!.text.contains("duplicate"))
            node(By.text("Done")).click(); assertTrue(device.wait(Until.gone(By.text("Session MCP servers")), 3000))
            val rename = host.send("/name New name"); host.onMain { host.chat.onData(rename, session, data("wrong kind")); host.chat.onAck(Ack(rename, session, true, "")) }
            assertNull(host.dialog()); host.notice("Session renamed"); assertEquals("Проверка дизайна", host.chat.title)
            assertEquals("New name", host.wire.commands.last().second.getString("name")); host.noPrompt(); capture("renamed")
        }
    }
    @Test fun replacingTheMcpWindowKeepsTheNewWindowOwnedAfterOldDismiss() {
        launch().use { scenario ->
            val host = Host(scenario); host.reply(host.send()); node(By.text("Session MCP servers")); val old = requireNotNull(host.dialog())
            host.reply(host.send(), "new filesystem"); node(By.textContains("new filesystem — connected")); val current = requireNotNull(host.dialog())
            assertNotSame(old, current); assertFalse(old.isShowing); assertTrue(current.isShowing)
            host.onMain { old.dismiss() }; assertSame(current, host.dialog()); host.noPrompt(); assertEquals(2, host.wire.reads)
            node(By.text("Done")).click(); assertTrue(device.wait(Until.gone(By.text("Session MCP servers")), 3000)); assertNull(host.dialog())
        }
    }
    @Test fun missingMcpBodyKeepsTheActualComposerAndExposesInlineFeedback() {
        launch().use { scenario ->
            val host = Host(scenario); val id = host.send()
            host.onMain { host.chat.onData(id, session, JSONObject().put("type", "document")); host.chat.onAck(Ack(id, session, true, "")) }
            host.notice("Pi returned no MCP status"); node(By.text("/mcp")); assertEquals("/mcp", host.chat.composer.text); assertNull(host.dialog())
            host.noPrompt(); capture("missing")
        }
    }
    @Test fun actualStopClicksWriteOnceUntilAckAndKeepStatusAndDraft() {
        launch().use { scenario ->
            val host = Host(scenario); host.onMain {
                host.chat.onSnapshot(Snapshot(session, SessionStatus.RUNNING, true, listOf(ChatMessage.remote("u", ChatMessage.Role.USER, "Running question", null, null)), false))
                ChatFixture.text(host.chat, "Retained stop draft")
            }
            repeat(3) { node(By.res(context.packageName, "stopButton")).click(); idle() }
            host.notice("Stop is already requested"); assertEquals(1, host.wire.aborts); assertEquals("Retained stop draft", host.chat.composer.text); host.noPrompt(); capture("stop")
            host.onMain { host.chat.onAck(Ack("abort-1", "foreign", true, "")) }; node(By.res(context.packageName, "stopButton")).click(); idle(); assertEquals(1, host.wire.aborts)
            host.onMain { host.chat.onAck(Ack("abort-1", session, true, "")) }; assertEquals(SessionStatus.RUNNING, host.chat.status)
            node(By.res(context.packageName, "stopButton")).click(); idle(); assertEquals(2, host.wire.aborts)
            host.onMain { host.chat.onAck(Ack("abort-2", session, false, "denied")) }; host.notice("Pi rejected the command: denied"); host.noPrompt()
        }
    }
    @Test fun actualStopStartRetainsLiveTicketsAndRetiresMissingWithoutReplay() {
        launch().use { scenario ->
            val host = Host(scenario); val id = host.send(); host.onMain { host.chat.abort(); host.wire.liveRequests = setOf(id, "abort-1") }
            scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED); idle()
            host.onMain { host.chat.send(CommandBuilder.Behavior.FOLLOW_UP); host.chat.abort() }; assertEquals(1, host.wire.reads); assertEquals(1, host.wire.aborts)
            scenario.moveToState(Lifecycle.State.CREATED); host.wire.liveRequests = emptySet(); scenario.moveToState(Lifecycle.State.RESUMED); idle()
            host.onMain { host.chat.onData(id, session, data("late")); host.chat.onAck(Ack(id, session, true, "")); host.chat.onAck(Ack("abort-1", session, true, "")) }
            assertNull(host.dialog()); assertEquals("/mcp", host.chat.composer.text); assertEquals(1, host.wire.reads); assertEquals(1, host.wire.aborts); host.noPrompt()
        }
    }
    @Test fun activityDestroyDismissesItsDialogAndRejectsAllLateControlEffects() {
        val scenario = launch(); lateinit var host: Host; lateinit var shown: AlertDialog; lateinit var id: String
        try { host = Host(scenario); host.reply(host.send()); node(By.text("Session MCP servers")); shown = requireNotNull(host.dialog())
            id = host.send(); host.onMain { host.chat.abort() }
        } finally { scenario.close() }
        assertFalse(shown.isShowing); assertNull(host.dialog())
        host.onMain { host.chat.onData(id, session, data("late")); host.chat.onAck(Ack(id, session, true, "")); host.chat.abort()
            val effect = ChatActivity::class.java.getDeclaredMethod("effect", ChatSession.Effect::class.java).apply { isAccessible = true }
            effect.invoke(host.activity, ChatSession.Effect.Mcp(data("late direct effect"))) }
        assertNull(host.dialog()); assertEquals(2, host.wire.reads); assertEquals(1, host.wire.aborts); assertEquals("/mcp", host.chat.composer.text); host.noPrompt()
    }
    @Test fun readOnlyScreenHasNoMutationControlsAndRejectsInjectedControlCommands() {
        launch(true).use { scenario ->
            val host = Host(scenario, true); host.send("/name Name"); host.send(); host.onMain { host.chat.abort(); host.chat.onData("read-1", session, data()) }
            node(By.res(context.packageName, "readOnlyBanner")); assertFalse(device.hasObject(By.res(context.packageName, "composerInput"))); assertFalse(device.hasObject(By.res(context.packageName, "stopButton")))
            assertNull(host.dialog()); assertEquals(0, host.wire.reads); assertEquals(0, host.wire.writes); assertEquals("/mcp", host.chat.composer.text); host.noPrompt()
        }
    }
}
