package ru.billyhargrove.pimobile.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.os.Build
import com.google.android.material.shape.MaterialShapeDrawable
import ru.billyhargrove.pimobile.R
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/** Bounded platform modal host. Its content and controls are Compose. */
open class ComposeSheet(context: Context) : BottomSheetDialog(context) {
    protected fun content(body: @Composable () -> Unit) {
        var base = context
        while (base is ContextWrapper && base !is ComponentActivity && base.baseContext !== base) base = base.baseContext
        val owner = base as? ComponentActivity
        val view = ComposeView(context).apply {
            if (owner != null) { setViewTreeLifecycleOwner(owner); setViewTreeSavedStateRegistryOwner(owner) }
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { PiTheme { Surface(color = MaterialTheme.colorScheme.surface) { body() } } }
        }
        setContentView(view)
        setOnShowListener {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED; behavior.skipCollapsed = true
            val shade = context.getColor(R.color.surface)
            val background = findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)?.background
            if (background is MaterialShapeDrawable) background.fillColor = ColorStateList.valueOf(shade)
            else background?.setTint(shade)
            window?.navigationBarColor = shade
            if (Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced = false
        }
    }
}
