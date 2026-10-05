package ru.billyhargrove.pimobile.ui;

import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/** One-row resting composer; keyboard expansion leaves every action's hit target 48dp. */
public final class CompactComposer {
    private final View editor;
    private final EditText input;
    private final float density;
    private boolean expanded;
    private float height = 48;
    private final SpringAnimation spring;
    public CompactComposer(View editor, EditText input) {
        this.editor = editor; this.input = input;
        density = editor.getResources().getDisplayMetrics().density;
        spring = new SpringAnimation(this, new FloatPropertyCompat<CompactComposer>("height") {
            public float getValue(CompactComposer c) { return c.height; }
            public void setValue(CompactComposer c, float value) {
                c.height = value;
                android.view.ViewGroup.LayoutParams p = c.editor.getLayoutParams();
                p.height = Math.round(Math.max(48, value) * c.density);
                c.editor.setLayoutParams(p);
            }
        });
        spring.setSpring(new SpringForce(48).setDampingRatio(.85f).setStiffness(500));
        editor.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(editor);
            boolean next = input.hasFocus() && insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
            if (next == expanded) return;
            expanded = next;
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams)input.getLayoutParams();
            p.setMarginStart(Math.round((next ? 0 : 48) * density));
            p.setMarginEnd(Math.round((next ? 0 : 144) * density));
            p.height = Math.round((next ? 64 : 48) * density);
            input.setLayoutParams(p);
            if (ExpressiveMotion.enabled()) spring.animateToFinalPosition(next ? 112 : 48);
            else { spring.cancel(); height = next ? 112 : 48; android.view.ViewGroup.LayoutParams e = editor.getLayoutParams(); e.height = Math.round(height * density); editor.setLayoutParams(e); }
        });
        editor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {}
            public void onViewDetachedFromWindow(View v) { spring.cancel(); }
        });
    }
}
