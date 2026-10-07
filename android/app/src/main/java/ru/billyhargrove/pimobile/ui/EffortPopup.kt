package ru.billyhargrove.pimobile.ui

import android.graphics.Rect
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.widget.*
import androidx.activity.*
import androidx.compose.ui.platform.*
import androidx.lifecycle.*
import androidx.savedstate.*
import ru.billyhargrove.pimobile.features.chat.QuickEffortSession
import ru.billyhargrove.pimobile.features.chat.QuickEffortScreen
import org.json.JSONObject
import java.util.function.Consumer
import java.util.function.Supplier

/** Anchored effort panel. Selections/ACK never close it; dismissal belongs to the user. */
class EffortPopup(anchor: View, anchorBounds: Supplier<Rect>, initialModel: JSONObject, current: String, editable: Boolean,
    openModels: Runnable, callback: Apply, currentTier: String?, changeTier: Consumer<String>?) : PopupWindow() {
    fun interface Apply { fun apply(level: String) }
    private val owner = QuickEffortSession(initialModel, current, editable, currentTier, callback::apply,
        changeTier?.let { change -> { value -> change.accept(value) } })
    internal val awaitingResult get() = owner.pending
    constructor(anchor: View, model: JSONObject, current: String, callback: Apply) : this(anchor, model, current, true, Runnable {}, callback)
    constructor(anchor: View, model: JSONObject, current: String, editable: Boolean, openModels: Runnable, callback: Apply) :
        this(anchor, model, current, editable, openModels, callback, "standard", null)
    constructor(anchor: View, model: JSONObject, current: String, editable: Boolean, openModels: Runnable, callback: Apply, currentTier: String?, changeTier: Consumer<String>?) :
        this(anchor, Supplier { val xy = IntArray(2); anchor.getLocationOnScreen(xy); Rect(xy[0], xy[1], xy[0] + anchor.width, xy[1] + anchor.height) },
            model, current, editable, openModels, callback, currentTier, changeTier)
    fun updateConfiguration(config: JSONObject) = owner.updateConfiguration(config)
    fun completed(failure: String?) = owner.completed(failure)
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
        root.addView(ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { PiTheme {
                QuickEffortScreen(owner, "${context.packageName}:id/", { openModels.run() }, ::dismiss)
            } }
        }, FrameLayout.LayoutParams(-1, -2))
        contentView = root
        val frame = Rect(); anchor.getWindowVisibleDisplayFrame(frame)
        width = frame.width() - 2 * pad; height = -2
        setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT)); elevation = 8 * density
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
        setOnDismissListener { owner.close(); back.remove(); if (anchor.viewTreeObserver.isAlive) anchor.viewTreeObserver.removeOnGlobalLayoutListener(follow) }
    }
}
