package ru.billyhargrove.pimobile.ui;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Edge-to-edge insets for the two screens.
 *
 * <p>With {@code targetSdk 35} the window is drawn behind the status and navigation
 * bars, so the roots must apply insets themselves. Policy:</p>
 * <ul>
 *   <li>left/right system-bar insets become padding on the root (gesture nav /
 *       cutouts / landscape);</li>
 *   <li>the surface-coloured top bar eats the status-bar inset so its colour runs
 *       under the status bar while the text stays readable;</li>
 *   <li>the bottom inset keys off the IME: when the keyboard is open the content is
 *       lifted above it, otherwise it is lifted above the navigation bar. When a
 *       bottom bar owns the navigation inset itself (main screen status strip), the
 *       root only reacts to the IME.</li>
 * </ul>
 *
 * <p>IME insets are reliably dispatched from API 30; on API 26–29 the activity is
 * {@code adjustResize} as well, so the composer stays visible on those versions
 * too.</p>
 */
public final class SystemInsets {

    private SystemInsets() {
    }

    public static void apply(final Activity activity,
                             final View root,
                             final View topBar,
                             final View bottomBar,
                             final boolean navBarOwnedByBottomBar) {
        if (activity == null || root == null) {
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        boolean dark = (activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        androidx.core.view.WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), root);
        controller.setAppearanceLightStatusBars(!dark);
        controller.setAppearanceLightNavigationBars(!dark);
        final int topBarPaddingTop = topBar == null ? 0 : topBar.getPaddingTop();
        final int bottomBarPaddingBottom = bottomBar == null ? 0 : bottomBar.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            int rootBottom = navBarOwnedByBottomBar
                    ? ime.bottom
                    : Math.max(bars.bottom, ime.bottom);
            view.setPadding(bars.left, 0, bars.right, rootBottom);
            if (topBar != null) {
                topBar.setPadding(topBar.getPaddingLeft(),
                        topBarPaddingTop + bars.top,
                        topBar.getPaddingRight(),
                        topBar.getPaddingBottom());
            }
            if (bottomBar != null) {
                bottomBar.setPadding(bottomBar.getPaddingLeft(),
                        bottomBar.getPaddingTop(),
                        bottomBar.getPaddingRight(),
                        bottomBarPaddingBottom + bars.bottom);
            }
            // Not consumed: nothing else in these screens competes for insets.
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
