package ru.billyhargrove.pimobile.ui

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import androidx.core.view.*

/** Edge-to-edge bridge for native rendering leaves and the Compose activity host. */
object SystemInsets {
    @JvmStatic fun apply(activity: Activity?, root: View?, topBar: View?, bottomBar: View?, navBarOwnedByBottomBar: Boolean) {
        if (activity == null || root == null) return
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val dark = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(activity.window, root).apply {
            isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
        }
        val top = topBar?.paddingTop ?: 0; val bottom = bottomBar?.paddingBottom ?: 0
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()); val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, 0, bars.right, if (navBarOwnedByBottomBar) ime.bottom else maxOf(bars.bottom, ime.bottom))
            topBar?.setPadding(topBar.paddingLeft, top + bars.top, topBar.paddingRight, topBar.paddingBottom)
            bottomBar?.setPadding(bottomBar.paddingLeft, bottomBar.paddingTop, bottomBar.paddingRight, bottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
