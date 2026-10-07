package ru.billyhargrove.pimobile

import android.content.res.Configuration
import android.graphics.Rect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.features.chat.ChatSession
import ru.billyhargrove.pimobile.ui.*

/** Native visual/interaction acceptance. No credentials, network commands, paid prompts or live agents. */
@RunWith(AndroidJUnit4::class)
class RedesignUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val session = "redesign-synthetic"
    private val density get() = context.resources.displayMetrics.density
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout = 1000
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
    }
    private fun node(id: String): UiObject2 = requireNotNull(device.wait(Until.findObject(By.res(context.packageName, id)), 5000)) { "Missing $id" }
    private fun idle() {
        instrumentation.waitForIdleSync(); device.waitForIdle(1000)
        // Re-fetch native accessibility nodes after Compose changed a virtual text ID.
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
    }
    private fun capture(name: String) {
        idle(); android.os.SystemClock.sleep(500)
        assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "redesign-$name.png")))
    }
    private fun launch(readOnly: Boolean = false) = ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, session, "Pi Mobile · Design pass", readOnly))
    private fun ready(scenario: ActivityScenario<ChatActivity>, readOnly: Boolean = false): ChatSession {
        lateinit var owner: ChatSession
        scenario.onActivity {
            owner = ChatFixture.install(it, session, readOnly, emptyList())
            it.onConfiguration(session, JSONObject("""{"model":"test/gpt-6.1-sol","thinkingLevel":"high","models":[{"provider":"test","id":"gpt-6.1-sol","name":"GPT-6.1 Sol","thinkingLevels":["low","medium","high"]}]}"""))
        }
        idle(); return owner
    }
    private fun show(owner: ChatSession, messages: List<ChatMessage>) {
        owner.onSnapshot(Snapshot(session, SessionStatus.IDLE, true, messages, false))
    }
    @Test fun conversationUsesFullWidthAssistantAndSeparateUserReceipt() {
        launch().use { scenario ->
            val owner = ready(scenario)
            val messages = listOf(
                ChatMessage.remote("u", ChatMessage.Role.USER, "Give our Android app a calmer, clearer design.", null, null).withPresentation("turn", "work", "", ""),
                ChatMessage("tool:read", ChatMessage.Role.TOOL_RESULT, "Reviewed the chat, composer and settings.", null, "read", ChatMessage.LocalState.NONE, "", "done").withPresentation("turn", "work", "ChatScreen.kt", ""),
                ChatMessage.remote("a", ChatMessage.Role.ASSISTANT,
                    "## Less chrome. More conversation.\n\nThe answer is the main event. Clear type, generous line spacing and a quiet background make long replies easier to read.\n\n**Your Pi stays your Pi.** Tools, attachments and workflows remain available without competing for attention.\n\n- A full-width writing area\n- Four familiar actions within reach\n- Light and dark surfaces that feel like one product", null, null).withPresentation("turn", "final", "", "")
            )
            scenario.onActivity { show(owner, messages) }; idle()
            // On a large-font screen older rows may be outside the viewport; inspect them by scrolling.
            val assistant = node("assistantMessage").visibleBounds
            assertTrue("Assistant should use the reading column", assistant.width() >= node("chatRoot").visibleBounds.width() - 44 * density)
            capture("conversation")
            scenario.onActivity { owner.readerDragged(); ChatFixture.list(it).requestScrollToItem(0) }
            idle()
            val user = node("messageBubble").visibleBounds
            val receipt = node("messageRead").visibleBounds
            assertTrue("Receipt is outside user bubble", receipt.top >= user.bottom)
            assertEquals("read", node("messageRead").text)
            assertTrue(device.hasObject(By.desc("Expand tools")))
            node("workHeader").click(); node("workLogList"); capture("tools")
            node("workHeader").click()
        }
    }
    @Test fun onlyNewestUserSubmissionHasReadAndTruncatedHistoryHasNoBanner() {
        launch().use { scenario ->
            val owner = ready(scenario)
            val history = listOf(
                ChatMessage.remote("first", ChatMessage.Role.USER, "Earlier question", null, null),
                ChatMessage.remote("second", ChatMessage.Role.USER, "Latest accepted", null, null),
                ChatMessage.remote("answer", ChatMessage.Role.ASSISTANT, "Agent reply", null, null))
            scenario.onActivity { owner.onSnapshot(Snapshot(session, SessionStatus.IDLE, true, history, true)) }
            node("messageRead"); idle()
            assertEquals(1, device.findObjects(By.res(context.packageName, "messageRead")).size)
            assertTrue(node("messageRead").visibleBounds.top > requireNotNull(device.findObject(By.text("Latest accepted"))).visibleBounds.bottom)
            assertFalse(device.hasObject(By.res(context.packageName, "truncatedBanner")))
            assertFalse(device.hasObject(By.textContains("Older messages")))
            scenario.onActivity { ChatFixture.text(owner, "Newest pending"); owner.send(CommandBuilder.Behavior.FOLLOW_UP) }
            assertTrue("A newer unacknowledged message must hide the old read receipt", device.wait(Until.gone(By.res(context.packageName, "messageRead")), 3000)); idle()
            scenario.onActivity { owner.onAck(Ack("request-1", session, true, "")) }
            node("messageRead"); idle()
            assertEquals(1, device.findObjects(By.res(context.packageName, "messageRead")).size)
            assertTrue(node("messageRead").visibleBounds.top > requireNotNull(device.findObject(By.text("Newest pending"))).visibleBounds.bottom)
            capture("latest-read")
        }
    }
    @Test fun toolHeaderRollsActualLatestNameAndCountWithoutToolsIconOrStatusText() {
        launch().use { scenario ->
            ready(scenario)
            val first = ChatMessage("tool:1", ChatMessage.Role.TOOL_RESULT, "Read source", null, "read", ChatMessage.LocalState.NONE, "", "done").withPresentation("turn", "work", "", "")
            val second = ChatMessage("tool:2", ChatMessage.Role.TOOL_RESULT, "Build", null, "bash", ChatMessage.LocalState.NONE, "", "running").withPresentation("turn", "work", "", "")
            scenario.onActivity { ChatFixture.show(it, listOf(first)) }
            idle(); assertEquals("read", node("workToolName").text); assertEquals("1", node("workToolCount").text)
            scenario.onActivity { ChatFixture.show(it, listOf(first, second)) }
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "workToolName").text("bash")), 3000))
            assertTrue("Outgoing name must roll away", device.wait(Until.gone(By.res(context.packageName, "workToolName").text("read")), 3000))
            idle(); assertEquals("bash", node("workToolName").text); assertEquals("2", node("workToolCount").text)
            assertFalse(device.hasObject(By.textContains("Tools ·"))); assertFalse(device.hasObject(By.text("Running")))
            node("workHeader").click(); assertTrue(device.wait(Until.gone(By.res(context.packageName, "workLogList")), 3000))
            assertEquals("bash", node("workToolName").text)
            capture("tool-roller")
            node("workHeader").click(); node("workLogList")
            assertTrue(device.hasObject(By.descContains("bash, running")))
        }
    }
    @Test fun catalogHeaderUsesActualHostAndIconOnlyConnectionStates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var state: ru.billyhargrove.pimobile.features.catalog.CatalogSession
            scenario.onActivity { state = CatalogFixture.install(it, Catalog.empty()); state.onConnectionState(ConnectionState.CONNECTED, "https://computer.example:8443") }
            idle(); assertEquals("computer.example:8443", node("mainHostTitle").text)
            assertEquals("Connected", node("connectionIndicator").contentDescription)
            val indicator = node("connectionIndicator").visibleBounds
            val host = node("mainHostTitle").visibleBounds
            val actions = node("mainHeaderActions").visibleBounds
            val refresh = node("refreshButton").visibleBounds
            val settings = node("settingsButton").visibleBounds
            assertTrue("Connection is attached to the left-hand host, not the action cluster", indicator.right < host.left && host.right < actions.left)
            assertEquals("Host/status gap is 8dp", 8 * density, (host.left - indicator.right).toFloat(), 1f)
            assertEquals(refresh.width(), settings.width()); assertEquals(refresh.height(), settings.height())
            assertEquals("Actions retain 48dp targets", 48 * density, refresh.width().toFloat(), 1f)
            assertEquals("Right-hand actions share one 4dp gap", 4 * density, (settings.left - refresh.right).toFloat(), 1f)
            assertFalse(device.hasObject(By.text("Pi Mobile"))); assertFalse(device.hasObject(By.res(context.packageName, "connectionStatusText")))
            scenario.onActivity { state.url = "https://unsubmitted.example" }
            idle(); assertEquals("computer.example:8443", node("mainHostTitle").text)
            for (connection in listOf(ConnectionState.CONNECTING, ConnectionState.RECONNECTING, ConnectionState.DISCONNECTED, ConnectionState.ERROR)) {
                scenario.onActivity { state.onConnectionState(connection, "Synthetic state") }
                idle(); node("connectionIndicator")
                assertFalse(device.hasObject(By.res(context.packageName, "connectionStatusText")))
            }
            capture("host-header-offline")
        }
    }
    @Test fun toolsHaveNoPressedRectangleOrBlanketErrorButKeepIndividualFailures() {
        launch().use { scenario ->
            val owner = ready(scenario)
            scenario.onActivity { show(owner, listOf(
                ChatMessage("tool:failed", ChatMessage.Role.TOOL_RESULT, "One command failed", null, "bash", ChatMessage.LocalState.NONE, "", "error").withPresentation("turn", "work", "failed command", ""),
                ChatMessage("tool:ok", ChatMessage.Role.TOOL_RESULT, "Read succeeded", null, "read", ChatMessage.LocalState.NONE, "", "done").withPresentation("turn", "work", "ChatScreen.kt", ""),
                ChatMessage.remote("a", ChatMessage.Role.ASSISTANT, "The next tool succeeded. A single failed call does not make the whole group an error.", null, null).withPresentation("turn", "final", "", "")
            )) }
            val header = node("workHeader").visibleBounds
            assertFalse(device.hasObject(By.text("Error")))
            val baseline = instrumentation.uiAutomation.takeScreenshot()
            val downAt = android.os.SystemClock.uptimeMillis()
            fun touch(action: Int) {
                val event = android.view.MotionEvent.obtain(downAt, android.os.SystemClock.uptimeMillis(), action, header.centerX().toFloat(), header.centerY().toFloat(), 0)
                event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN)
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)); event.recycle()
            }
            touch(android.view.MotionEvent.ACTION_DOWN)
            try {
                android.os.SystemClock.sleep(180)
                val pressed = instrumentation.uiAutomation.takeScreenshot()
                val x = header.right - (6 * density).toInt(); val y = header.centerY()
                assertEquals("Pressing tools must not paint a rectangular active zone", baseline.getPixel(x, y), pressed.getPixel(x, y))
                pressed.recycle()
                assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "redesign-tools-pressed.png")))
            } finally { touch(android.view.MotionEvent.ACTION_UP); baseline.recycle() }
            node("workLogList")
            assertTrue("A real failure remains specific to its call", device.hasObject(By.descContains("bash, error")))
            assertTrue(device.hasObject(By.descContains("read, done")))
            capture("tools-individual-error")
            node("workHeader").click()
            assertTrue(device.wait(Until.gone(By.res(context.packageName, "workLogList")), 3000))
        }
    }
    @Test fun inlineCodeUrlsHaveNoFalseSelectionBackgroundAndRemainSelectable() {
        launch().use { scenario ->
            val owner = ready(scenario)
            val url = "https://example.org/imagine/post/5275b2b9-eaa0-430d-a65f-c61bdf223d72"
            scenario.onActivity { show(owner, listOf(ChatMessage.remote("a", ChatMessage.Role.ASSISTANT, "**06:** `$url`\n\nThis is inline code, not selected text.", null, null))) }
            node("assistantMessage"); idle(); android.os.SystemClock.sleep(700)
            scenario.onActivity { a ->
                val view = a.findViewById<android.widget.TextView>(R.id.messageText)
                assertTrue(view.isTextSelectable)
                val text = view.text as android.text.Spanned
                val spans = text.getSpans(0, text.length, io.noties.markwon.core.spans.CodeSpan::class.java)
                assertEquals(1, spans.size)
                val paint = android.text.TextPaint(view.paint); spans.single().updateDrawState(paint)
                assertEquals("Wrapped inline code must not look like gray selection strips", 0, android.graphics.Color.alpha(paint.bgColor))
                assertTrue(text.toString().contains(url))
            }
            capture("inline-code-url")
            node("messageText").longClick(); idle()
            scenario.onActivity { a ->
                val view = a.findViewById<android.widget.TextView>(R.id.messageText)
                assertTrue("Actual text selection still works", view.selectionStart >= 0 && view.selectionEnd > view.selectionStart)
            }
            capture("actual-selection")
            device.pressBack()
        }
    }
    @Test fun settingsSlideAndReturnPreserveCatalogPositionAndUserFields() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var state: ru.billyhargrove.pimobile.features.catalog.CatalogSession
            scenario.onActivity { a -> state = CatalogFixture.install(a, Catalog(listOf(Workspace("w", "Workspace", "/synthetic")),
                List(24) { index -> Session("s-$index", "Conversation $index", "/synthetic", "w", "t-$index", true, SessionStatus.IDLE, "test/model") }, null)) }
            node("catalogList").scroll(Direction.DOWN, .7f); idle()
            val before = device.findObjects(By.res(context.packageName, "rowTitle")).map { it.text }
            assertTrue(before.isNotEmpty())
            // Capture the transition without UiAutomator's animation-idle wait.
            scenario.onActivity { state.screen = ru.billyhargrove.pimobile.features.catalog.CatalogSession.Screen.Settings }
            repeat(6) { index ->
                android.os.SystemClock.sleep(80)
                assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "redesign-settings-slide-$index.png")))
            }
            node("connectPanel"); idle()
            node("connectUrlInput").click()
            assertTrue(device.wait(Until.hasObject(By.res("android:id/input_method_nav_back")), 5000))
            node("connectUrlInput").text = "https://example.invalid/edited"
            device.pressBack(); idle() // Hide IME only, not the page.
            assertEquals(ru.billyhargrove.pimobile.features.catalog.CatalogSession.Screen.Settings, state.screen)
            node("navigationButton").click(); node("catalogList"); idle()
            assertEquals("Returning should restore the catalog viewport", before, device.findObjects(By.res(context.packageName, "rowTitle")).map { it.text })
            node("settingsButton").click(); node("connectPanel"); idle()
            assertEquals("https://example.invalid/edited", state.url)
            capture("settings-return")
        }
    }
    @Test fun composerKeepsFullWritingWidthAboveFourActionsAndPreservesLongDraft() {
        launch().use { scenario ->
            val owner = ready(scenario)
            val input = node("composerInput").visibleBounds
            assertTrue("Input no longer squeezed between four icons", input.width() > node("composerContainer").visibleBounds.width() * .80)
            val actionBounds = listOf("attachImageButton", "deliveryButton", "effortButton", "sendButton").map { node(it).visibleBounds }
            actionBounds.forEach { bounds ->
                assertTrue("Writing row does not overlap controls", input.bottom <= bounds.top)
                assertTrue(bounds.width() >= 48 * density - 1 && bounds.height() >= 48 * density - 1)
                assertTrue(node("composerContainer").visibleBounds.contains(bounds))
            }
            assertEquals(4, actionBounds.size)
            capture("empty")
            node("composerInput").click()
            assertTrue(device.wait(Until.hasObject(By.res("android:id/input_method_nav_back")), 5000))
            val draft = "A longer draft with enough room to read and edit.\nKeep all four composer actions available."
            node("composerInput").text = draft; idle(); capture("keyboard")
            device.pressBack(); idle()
            repeat(10) {
                android.os.SystemClock.sleep(100)
                scenario.onActivity { a ->
                    assertFalse("IME must stay dismissed", ViewCompat.getRootWindowInsets(a.findViewById(android.R.id.content))?.isVisible(WindowInsetsCompat.Type.ime()) == true)
                    assertEquals(draft, owner.composer.text)
                }
            }
            capture("draft-hidden-keyboard")
        }
    }
    @Test fun jumpToLatestReturnsToLongAnswerWithoutSendingAnything() {
        launch().use { scenario ->
            val owner = ready(scenario)
            scenario.onActivity {
                show(owner, listOf(ChatMessage.remote("long", ChatMessage.Role.ASSISTANT,
                    List(100) { index -> "Line $index of a long answer." }.joinToString("\n\n"), null, null)))
            }
            node("assistantMessage"); idle()
            scenario.onActivity { owner.readerDragged(); ChatFixture.list(it).requestScrollToItem(0) }
            node("jumpToLatest").click()
            assertTrue("Jump should finish its layout/scroll work", device.wait(Until.gone(By.res(context.packageName, "jumpToLatest")), 5000))
            idle()
            scenario.onActivity { assertFalse("Jump should reach the end, including a long final message", ChatFixture.list(it).canScrollForward) }
            assertTrue(owner.followTail)
        }
    }
    @Test fun readOnlyKeepsTheSameReadingDesignWithoutCommandControls() {
        launch(true).use { scenario ->
            val owner = ready(scenario, true)
            scenario.onActivity { show(owner, listOf(ChatMessage.remote("a", ChatMessage.Role.ASSISTANT, "## Agent activity\n\nThis conversation is read-only. The same typography and tools are used without a composer or command controls.", null, null))) }
            node("assistantMessage")
            assertFalse(device.hasObject(By.res(context.packageName, "composerContainer")))
            assertFalse(device.hasObject(By.res(context.packageName, "sendButton")))
            scenario.onActivity { owner.onSnapshot(Snapshot(session, SessionStatus.RUNNING, true, listOf(ChatMessage.remote("a", ChatMessage.Role.ASSISTANT, "Read-only running agent", null, null)), false)) }
            idle(); assertFalse(device.hasObject(By.res(context.packageName, "stopButton")))
            capture("read-only")
        }
    }
    @Test fun catalogAndHistoryUseRealRowsAndConsistentSurfaces() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val workspace = Workspace("design", "pi-mobile", "/synthetic")
            val catalog = Catalog(listOf(workspace), listOf(
                Session("design", "Polish the Android interface", "/synthetic", "design", "terminal-1", true, SessionStatus.RUNNING, "test/gpt-6.1-sol"),
                Session("notes", "Research notes", "/synthetic", "design", "terminal-2", true, SessionStatus.IDLE, "test/model")), null)
            scenario.onActivity { a ->
                CatalogFixture.install(a, catalog)
                val providers = JSONArray()
                listOf("codex" to 36, "cursor" to 62, "grok" to 18).forEach { (name, used) -> providers.put(JSONObject().put("provider", name).put("status", "ok")
                    .put("updatedAt", System.currentTimeMillis()).put("windows", JSONArray().put(JSONObject().put("name", "weekly").put("usedPercent", used).put("resetsAt", System.currentTimeMillis() + 86400000)))) }
                CatalogFixture.usage(a).render(JSONObject().put("providers", providers))
            }
            node("historyButton"); idle(); capture("catalog")
            val rows = device.findObjects(By.res(context.packageName, "catalogRowRoot"))
            assertEquals(2, rows.size)
            assertTrue("Workspace is an explicitly DIFFERENT label, not another chat title", node("groupTitle").text.startsWith("Workspace · "))
            assertTrue("Chat indentation reinforces membership without a bubble", node("rowTitle").visibleBounds.left >= node("groupTitle").visibleBounds.left + 12 * density)
            if (context.resources.configuration.fontScale <= 1.3f) rows.forEach {
                assertTrue("Chat list rows are compact, not two-line cards", it.visibleBounds.height() <= 52 * density)
            }
            assertEquals(1, device.findObjects(By.res(context.packageName, "rowRunningSpinner")).size)
            listOf("rowBadge", "rowStatusDot", "rowSubtitle").forEach { assertFalse(device.hasObject(By.res(context.packageName, it))) }
            assertFalse(device.hasObject(By.text("test/gpt-6.1-sol")))
            val image = instrumentation.uiAutomation.takeScreenshot()
            val idleRow = rows.last().visibleBounds
            assertEquals("The destructive swipe underlay must not leak through a plain chat row at rest",
                context.getColor(R.color.bg), image.getPixel(idleRow.right - (6 * density).toInt(), idleRow.centerY()))
            image.recycle()
            val saved = JSONObject().put("sessions", JSONArray().put(JSONObject().put("id", "saved-design").put("title", "A quieter interface for Pi")
                .put("workspaceName", "pi-mobile").put("messageCount", 24).put("modified", java.time.Instant.now().toString()))).put("hasMore", false)
            var sheet: ArchiveSheet? = null
            scenario.onActivity { a -> sheet = ArchiveSheet(a, ArchiveSheet.Loader { _, _ -> saved }, ArchiveSheet.Listener { _, _ -> fail("Preview must not resume Pi") }); sheet!!.show() }
            node("archiveRow"); capture("history")
            scenario.onActivity { sheet!!.dismiss() }
        }
    }
    @Test fun transcriptIsTheFullCanvasBehindFloatingChrome() {
        launch().use { scenario ->
            val owner = ready(scenario)
            scenario.onActivity { show(owner, List(20) { index -> ChatMessage.remote("u-$index", ChatMessage.Role.USER, "Message $index stays on the continuous chat canvas.", null, null) }) }
            val root = node("chatRoot").visibleBounds
            val list = node("messageList").visibleBounds
            val top = node("chatTopBar").visibleBounds
            val composer = node("composerContainer").visibleBounds
            assertEquals(root.top, list.top); assertEquals(root.bottom, list.bottom)
            assertTrue("Transcript extends behind the header", list.top < top.top)
            assertTrue("Transcript extends behind input/navigation", list.bottom > composer.bottom)
            assertTrue("Chrome must be an island, not a full-width bar", composer.width() < root.width())
            node("chatTopFade"); node("chatBottomFade")
            scenario.onActivity { owner.readerDragged(); ChatFixture.list(it).requestScrollToItem(10, 30) }
            idle(); capture("islands")
        }
    }
    @Test fun queueAboveComposerIsBoundedExpandableAndOnlyEchoConsumesIt() {
        launch().use { scenario ->
            val owner = ready(scenario)
            scenario.onActivity {
                owner.onSnapshot(Snapshot(session, SessionStatus.RUNNING, true, listOf(ChatMessage.remote("original", ChatMessage.Role.ASSISTANT, "The original task is still running.", null, null)), false))
                repeat(5) { index -> ChatFixture.text(owner, "Queued task $index"); owner.send(CommandBuilder.Behavior.FOLLOW_UP); owner.onAck(Ack("request-${index + 1}", session, true, "")) }
                assertEquals(5, owner.queue.size); assertEquals(1, owner.items.size)
            }
            assertTrue(node("messageQueue").visibleBounds.bottom <= node("composerContainer").visibleBounds.top)
            node("queueHeader").click(); idle()
            assertTrue(node("messageQueue").visibleBounds.height() <= 186 * density + 2)
            capture("queue")
            scenario.onActivity {
                owner.onMessages(MessagesUpdate(session, listOf(ChatMessage.remote("echo", ChatMessage.Role.USER, "Queued task 0", null, null)), emptyList(), SessionStatus.RUNNING, true, false, false, false, false))
                assertEquals(4, owner.queue.size)
            }
            capture("queue-after-echo")
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "queue-after-echo.xml"))
            assertTrue("Queue label must reflect the consumed echo", device.wait(Until.hasObject(By.desc("Queue: 4 messages")), 3000))
            node("queueHeader").click(); node("composerInput").click()
            val shown = device.wait(Until.hasObject(By.res("android:id/input_method_nav_back")), 5000)
            if (!shown) {
                capture("queue-keyboard-request-failed")
                device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "queue-keyboard-request-failed.xml"))
                scenario.onActivity { a -> File(context.getExternalFilesDir(null), "queue-keyboard-request-failed.txt").writeText(
                    "focus=${a.window.decorView.findFocus()?.javaClass?.name}, ime=${ViewCompat.getRootWindowInsets(a.findViewById(android.R.id.content))?.isVisible(WindowInsetsCompat.Type.ime())}") }
            }
            assertTrue("Queue must not prevent opening the actual keyboard", shown); capture("queue-keyboard")
            device.pressBack()
        }
    }
    private fun liveActivity(): JSONObject = JSONObject("""{"liveAvailable":true,"available":true,"workflows":[{"id":"w","title":"Design and verify","status":"running","startedAt":1,"agentCount":2,"completed":1,"phases":[{"title":"Review","status":"completed","agentCount":1,"completed":1},{"title":"Build","status":"running","agentCount":1,"completed":0},{"title":"Verify","status":"queued","agentCount":0,"completed":0}]}],"agents":[{"id":"a","workflowId":"w","phase":"Review","name":"Review visual hierarchy","status":"completed","model":"test/model","toolCalls":3,"outputTokens":420,"canInspect":true},{"id":"b","workflowId":"w","phase":"Build","name":"Build the floating chat","status":"running","model":"test/model","thinkingLevel":"high","toolCalls":12,"outputTokens":1840,"canInspect":true},{"id":"solo","name":"Check accessibility","status":"running","toolCalls":7,"outputTokens":1200,"canInspect":true}]}""").apply {
        getJSONArray("workflows").getJSONObject(0).put("startedAt", System.currentTimeMillis() - 32000)
        getJSONArray("agents").getJSONObject(1).put("startedAt", System.currentTimeMillis() - 21000)
        getJSONArray("agents").getJSONObject(2).put("startedAt", System.currentTimeMillis() - 12000)
    }
    @Test fun liveAgentsAndWorkflowHaveSeparateDocksAndFinishedOrSavedActivityDisappears() {
        launch().use { scenario ->
            ready(scenario)
            val data = liveActivity()
            scenario.onActivity { ChatFixture.orchestration(it, data) }
            node("activeWorkflow"); node("activeAgents")
            assertTrue(device.hasObject(By.textContains("7 tools"))); assertTrue(device.hasObject(By.textContains("1200 tokens")))
            assertFalse(device.hasObject(By.res(context.packageName, "orchestrationButton")))
            capture("active-docks")
            scenario.onActivity { ChatFixture.orchestration(it, JSONObject(data.toString()).put("liveAvailable", false)) }
            assertTrue(device.wait(Until.gone(By.res(context.packageName, "activeOrchestration")), 3000))
            data.getJSONArray("workflows").getJSONObject(0).put("status", "completed")
            (0 until data.getJSONArray("agents").length()).forEach { data.getJSONArray("agents").getJSONObject(it).put("status", "completed") }
            scenario.onActivity { ChatFixture.orchestration(it, data) }
            assertFalse(device.hasObject(By.res(context.packageName, "activeOrchestration")))
            node("orchestrationHistory") // Saved activity remains reachable without a permanent plaque.
        }
    }
    @Test fun busyWorkflowQueueAndKeyboardKeepInputAndHeaderReachableTogether() {
        launch().use { scenario ->
            val owner = ready(scenario)
            scenario.onActivity { a ->
                owner.onTimelineMeta(JSONObject().put("sessionId", session).put("activeTurnId", "turn").put("turns", JSONArray().put(
                    JSONObject().put("id", "turn").put("startedAt", System.currentTimeMillis() - 32000))))
                owner.onSnapshot(Snapshot(session, SessionStatus.RUNNING, true, listOf(ChatMessage.remote("answer", ChatMessage.Role.ASSISTANT,
                    "The workflow is running. I will keep the original task going while your next messages wait in Queue.", null, null)), false))
                repeat(2) { ChatFixture.text(owner, "Follow-up $it"); owner.send(CommandBuilder.Behavior.FOLLOW_UP); owner.onAck(Ack("request-${it + 1}", session, true, "")) }
                ChatFixture.orchestration(a, liveActivity())
            }
            node("composerInput").click()
            assertTrue(device.wait(Until.hasObject(By.res("android:id/input_method_nav_back")), 5000)); idle()
            capture("busy-queue-keyboard")
            val top = node("chatTopBar").visibleBounds
            val dock = node("activeOrchestration").visibleBounds
            val input = node("composerContainer").visibleBounds
            assertTrue("Busy dock must not cover the header", dock.top >= top.bottom)
            assertTrue("Some reading room must survive combined busy/queue/IME state", dock.top - top.bottom >= 48 * density)
            assertTrue(input.top > dock.top)
            listOf("attachImageButton", "deliveryButton", "effortButton", "sendButton").forEach { id -> assertTrue(input.contains(node(id).visibleBounds)) }
            scenario.onActivity { assertEquals(2, owner.queue.size) }
            device.pressBack()
        }
    }
    @Test fun workflowDetailGroupsActualAgentsByStageWithToolsTokensAndClock() {
        ActivityScenario.launch<OrchestrationActivity>(OrchestrationActivity.intent(context, session, "workflow", "w", "Design and verify")).use { scenario ->
            scenario.onActivity { it.stopUpdates(); it.render(liveActivity()) }
            node("workflowPhase")
            val list = node("orchestrationList")
            repeat(8) { if (!device.hasObject(By.text("Build the floating chat"))) list.scroll(Direction.DOWN, .5f) }
            val agent = requireNotNull(device.wait(Until.findObject(By.text("Build the floating chat")), 3000))
            val phase = requireNotNull(device.findObject(By.text("Build")))
            assertTrue(phase.visibleBounds.top < agent.visibleBounds.top)
            repeat(8) { if (!device.hasObject(By.textContains("12 tools · 1840 tokens"))) { node("orchestrationList").scroll(Direction.DOWN, .15f); idle() } }
            if (!device.hasObject(By.textContains("12 tools · 1840 tokens"))) {
                capture("workflow-metrics-unreachable")
                device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "workflow-metrics-unreachable.xml"))
            }
            assertTrue("Recorded agent metrics must be reachable", device.hasObject(By.textContains("12 tools · 1840 tokens")))
            assertFalse(device.hasObject(By.res(context.packageName, "composerContainer")))
            capture("workflow-stages")
        }
    }
    @Test fun materialThemeDefinesNeutralContainersForActualSystemTheme() {
        val observed = AtomicReference<ColorScheme>()
        val typography = AtomicReference<androidx.compose.material3.Typography>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                CatalogFixture.install(a, Catalog.empty())
                a.setContentView(ComposeView(a).apply { setContent { PiTheme { val scheme = MaterialTheme.colorScheme; val type = MaterialTheme.typography; SideEffect { observed.set(scheme); typography.set(type) } } } })
            }
            idle()
            val scheme = requireNotNull(observed.get())
            val type = requireNotNull(typography.get())
            assertTrue("Content titles must not share group-label size", type.bodyLarge.fontSize.value > type.labelMedium.fontSize.value)
            assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, type.bodyLarge.fontWeight)
            assertEquals(androidx.compose.ui.text.font.FontWeight.Medium, type.labelMedium.fontWeight)
            assertEquals("Numeric status does not jitter with changing digits", "tnum", type.bodySmall.fontFeatureSettings)
            assertEquals(context.getColor(R.color.bg), scheme.background.toArgb())
            assertEquals(context.getColor(R.color.surface), scheme.surfaceContainer.toArgb())
            assertEquals(context.getColor(R.color.accent_soft), scheme.primaryContainer.toArgb())
            assertEquals(context.getColor(R.color.secondary_soft), scheme.secondaryContainer.toArgb())
            assertEquals(context.getColor(R.color.text_primary), scheme.onSurface.toArgb())
            assertEquals(context.getColor(R.color.bg), context.getColor(R.color.bubble_assistant))
            val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            assertTrue(if (dark) scheme.surface.red < .2f else scheme.surface.red > .9f)
        }
    }
}
