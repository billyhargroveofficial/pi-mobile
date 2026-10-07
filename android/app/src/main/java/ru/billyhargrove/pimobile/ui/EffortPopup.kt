package ru.billyhargrove.pimobile.ui

import android.graphics.Rect
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.widget.*
import androidx.activity.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.savedstate.*
import org.json.JSONObject
import java.util.function.Consumer
import java.util.function.Supplier
import ru.billyhargrove.pimobile.R

/** Anchored effort panel. Selections/ACK never close it; dismissal belongs to the user. */
class EffortPopup(anchor: View, anchorBounds: Supplier<Rect>, initialModel: JSONObject, current: String, editable: Boolean,
    openModels: Runnable, callback: Apply, currentTier: String?, changeTier: Consumer<String>?) : PopupWindow() {
    fun interface Apply { fun apply(level: String) }
    private var model by mutableStateOf(initialModel)
    private var selected by mutableStateOf(current)
    private var tier by mutableStateOf(currentTier ?: "standard")
    private var pending by mutableStateOf(false)
    private var error by mutableStateOf("")
    private var confirmedLevel = current
    private var confirmedTier = tier
    constructor(anchor: View, model: JSONObject, current: String, callback: Apply) : this(anchor, model, current, true, Runnable {}, callback)
    constructor(anchor: View, model: JSONObject, current: String, editable: Boolean, openModels: Runnable, callback: Apply) :
        this(anchor, model, current, editable, openModels, callback, "standard", null)
    constructor(anchor: View, model: JSONObject, current: String, editable: Boolean, openModels: Runnable, callback: Apply, currentTier: String?, changeTier: Consumer<String>?) :
        this(anchor, Supplier { val xy = IntArray(2); anchor.getLocationOnScreen(xy); Rect(xy[0], xy[1], xy[0] + anchor.width, xy[1] + anchor.height) },
            model, current, editable, openModels, callback, currentTier, changeTier)
    fun updateConfiguration(config: JSONObject) {
        val models = config.optJSONArray("models")
        (0 until (models?.length() ?: 0)).mapNotNull { models?.optJSONObject(it) }
            .find { "${it.optString("provider")}/${it.optString("id")}" == config.optString("model") }?.let { model = it }
        selected = config.optString("thinkingLevel", selected); confirmedLevel = selected
        tier = config.optString("serviceTier", tier); confirmedTier = tier
    }
    fun completed(failure: String?) {
        pending = false; error = failure.orEmpty()
        if (failure != null) { selected = confirmedLevel; tier = confirmedTier }
    }
    init {
        val context = anchor.context; val density = context.resources.displayMetrics.density; val pad = (16 * density).toInt()
        val root = object : FrameLayout(context) {
            override fun onAttachedToWindow() {
                rootView.setViewTreeLifecycleOwner(anchor.findViewTreeLifecycleOwner())
                rootView.setViewTreeSavedStateRegistryOwner(anchor.findViewTreeSavedStateRegistryOwner())
                super.onAttachedToWindow()
            }
        }
        root.setViewTreeLifecycleOwner(anchor.findViewTreeLifecycleOwner())
        root.setViewTreeSavedStateRegistryOwner(anchor.findViewTreeSavedStateRegistryOwner())
        fun tag(name: String) = Modifier.testTag("${context.packageName}:id/$name").semantics { testTagsAsResourceId = true }
        root.addView(ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { PiTheme {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)).padding(16.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { openModels.run() }, enabled = !pending,
                            modifier = tag("popupModelButton").weight(1f).heightIn(min = 48.dp)) {
                            Text(model.optString("name", model.optString("id")) + " ▾", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        if (TierToggle.supportsFast(model.optJSONArray("serviceTiers")) && changeTier != null) {
                            val fast = tier == "fast"
                            OutlinedButton(onClick = {
                                tier = if (fast) "standard" else "fast"; pending = true; error = ""; changeTier.accept(tier)
                            }, enabled = editable && !pending,
                                modifier = tag("quickTierButton").heightIn(min = 48.dp).semantics { contentDescription = "Processing tier: ${if (fast) "Fast" else "Standard"}. Switch to ${if (fast) "Standard" else "Fast"}" }) {
                                Text(if (fast) "ϟ Fast" else "Standard", fontSize = 11.sp)
                            }
                        }
                        IconButton(onClick = { dismiss() }, modifier = tag("closeEffortPanel").size(48.dp)) {
                            Icon(painterResource(R.drawable.ic_close), "Close effort panel")
                        }
                    }
                    Row(tag("effortTitle").padding(top = 12.dp, bottom = 16.dp)) {
                        Text("Effort  ", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(EffortSlider.label(selected), fontSize = 18.sp, color = Color(if (selected in listOf("max", "xhigh")) 0xffacb6ff else 0xfff38ac5))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Faster", Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Smarter", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AndroidView(factory = { EffortSlider(it).apply { id = R.id.quickEffortSlider } }, update = { slider ->
                        val levels = model.optJSONArray("thinkingLevels")
                        val signature = model.optString("provider") + "/" + model.optString("id") + "|" + levels
                        if (slider.tag != signature) {
                            slider.tag = signature
                            slider.configure(levels, selected) { value, done ->
                                val changed = value != confirmedLevel
                                selected = value
                                if (done && editable && !pending && changed) {
                                    pending = true; error = ""; callback.apply(value)
                                }
                            }
                        }
                        val index = (0 until (levels?.length() ?: 0)).indexOfFirst { levels?.optString(it) == selected }
                        if (index >= 0 && slider.getProgress() != index) slider.setProgress(index)
                        slider.isEnabled = editable && !pending && (levels?.length() ?: 0) > 1
                    }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(64.dp))
                    if (pending) Row(tag("effortPending").padding(top = 8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Waiting for Pi…", Modifier.padding(start = 8.dp), fontSize = 12.sp)
                    }
                    if (error.isNotEmpty()) Text(error, tag("effortError").padding(top = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            } }
        }, FrameLayout.LayoutParams(-1, -2))
        contentView = root
        val frame = Rect(); anchor.getWindowVisibleDisplayFrame(frame)
        width = frame.width() - 2 * pad; height = -2
        setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT)); elevation = 12 * density
        isFocusable = false; isOutsideTouchable = true; inputMethodMode = INPUT_METHOD_NEEDED; softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        showAtLocation(anchor, Gravity.TOP or Gravity.LEFT, frame.left + pad, frame.top)
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                root.viewTreeObserver.removeOnPreDrawListener(this)
                update(frame.left + pad, maxOf(frame.top, anchorBounds.get().top - root.measuredHeight - pad), -1, -1)
                root.pivotX = root.measuredWidth * .75f; root.pivotY = root.measuredHeight.toFloat(); ExpressiveMotion.reveal(root)
                return true
            }
        })
        val back = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { dismiss() } }
        (context as? OnBackPressedDispatcherOwner)?.onBackPressedDispatcher?.addCallback(back)
        val follow = ViewTreeObserver.OnGlobalLayoutListener {
            if (isShowing) { val visible = Rect(); anchor.getWindowVisibleDisplayFrame(visible)
                update(visible.left + pad, maxOf(visible.top, anchorBounds.get().top - root.measuredHeight - pad), -1, -1) }
        }
        anchor.viewTreeObserver.addOnGlobalLayoutListener(follow)
        setOnDismissListener { back.remove(); if (anchor.viewTreeObserver.isAlive) anchor.viewTreeObserver.removeOnGlobalLayoutListener(follow) }
    }
}
