package ru.billyhargrove.pimobile

import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.json.JSONArray
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.ui.EffortSlider

/** Actual Compose pointer/accessibility events; callbacks are isolated from any live Pi. */
@RunWith(AndroidJUnit4::class)
class EffortInteractionUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    @Before fun isolate() { androidx.test.uiautomator.Configurator.getInstance().setWaitForIdleTimeout(1000); instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() } }
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000) }
    private fun launch() = ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, "effort-fixture", "Effort", false))
    private fun mount(activity: ChatActivity, callback: EffortSlider.Change): EffortSlider {
        val slider = EffortSlider(activity)
        slider.configure(JSONArray("[\"low\",\"medium\",\"high\",\"xhigh\",\"max\"]"), "low", callback)
        android.transition.TransitionManager.endTransitions(activity.window.decorView as android.view.ViewGroup)
        activity.window.enterTransition = null
        val root = FrameLayout(activity)
        val density = activity.resources.displayMetrics.density
        root.addView(slider, FrameLayout.LayoutParams((300 * density).toInt(), (64 * density).toInt(), android.view.Gravity.CENTER))
        activity.setContentView(root)
        return slider
    }
    private fun touch(action: Int, x: Float, y: Float, down: Long) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)); event.recycle()
    }
    @Test fun draggingPreviewsOnlyAndReleaseCommitsOnce() {
        var commits = 0
        var committed = ""
        launch().use { scenario ->
            lateinit var slider: EffortSlider
            scenario.onActivity { slider = mount(it) { value, done -> if (done) { commits++; committed = value } } }
            idle()
            val bounds = device.wait(Until.findObject(By.res(context.packageName, "effortTrack")), 5000).visibleBounds
            val down = SystemClock.uptimeMillis()
            touch(MotionEvent.ACTION_DOWN, bounds.left + 25f, bounds.centerY().toFloat(), down)
            // Move over real frames so input resampling sees a finger's velocity.
            for (step in 1..8) {
                SystemClock.sleep(20)
                touch(MotionEvent.ACTION_MOVE, bounds.left + 25f + (bounds.centerX() - bounds.left - 25f) * step / 8,
                    bounds.centerY().toFloat(), down)
            }
            scenario.onActivity { assertEquals("high", slider.value()); assertEquals(0, commits) }
            touch(MotionEvent.ACTION_UP, bounds.right - 25f, bounds.centerY().toFloat(), down); idle()
            assertEquals(1, commits); assertEquals("max", committed)
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "effort-brain-max.png"))
        }
    }
    @Test fun cancellationRestoresGestureStartAndDisabledControlCannotCommit() {
        var commits = 0
        launch().use { scenario ->
            lateinit var slider: EffortSlider
            scenario.onActivity { slider = mount(it) { _, done -> if (done) commits++ } }; idle()
            val bounds = device.wait(Until.findObject(By.res(context.packageName, "effortTrack")), 5000).visibleBounds
            val down = SystemClock.uptimeMillis()
            touch(MotionEvent.ACTION_DOWN, bounds.centerX().toFloat(), bounds.centerY().toFloat(), down)
            touch(MotionEvent.ACTION_CANCEL, bounds.centerX().toFloat(), bounds.centerY().toFloat(), down); idle()
            scenario.onActivity { assertEquals("low", slider.value()); assertEquals(0, commits); slider.isEnabled = false }
            idle(); device.swipe(bounds.left + 30, bounds.centerY(), bounds.right - 30, bounds.centerY(), 20); idle()
            scenario.onActivity { assertEquals("low", slider.value()); assertEquals(0, commits) }
        }
    }
    @Test fun accessibilityRangeCommitsActualSupportedLevel() {
        var commits = 0
        var value = ""
        launch().use { scenario ->
            scenario.onActivity { mount(it) { level, done -> if (done) { value = level; commits++ } } }; idle()
            fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.contentDescription?.toString() == "Effort level" && node.rangeInfo != null) return node
                for (index in 0 until node.childCount) find(node.getChild(index))?.let { return it }
                return null
            }
            val node = requireNotNull(find(instrumentation.uiAutomation.rootInActiveWindow))
            assertEquals(0f, node.rangeInfo.min, 0f); assertEquals(4f, node.rangeInfo.max, 0f)
            val args = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 3f) }
            assertTrue(node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, args)); idle()
            assertEquals("xhigh", value); assertEquals(1, commits)
        }
    }
    @Test fun keyboardPreviewsAndCommitsOnKeyRelease() {
        var commits = 0
        launch().use { scenario ->
            lateinit var slider: EffortSlider
            scenario.onActivity { slider = mount(it) { _, done -> if (done) commits++ } }; idle()
            val bounds = device.wait(Until.findObject(By.res(context.packageName, "effortTrack")), 5000).visibleBounds
            device.click(bounds.left + 25, bounds.centerY()); idle()
            val before = commits
            instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)); idle()
            scenario.onActivity { assertEquals("medium", slider.value()); assertEquals(before, commits) }
            instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)); idle()
            scenario.onActivity { assertEquals("medium", slider.value()); assertEquals(before + 1, commits) }
        }
    }
}
