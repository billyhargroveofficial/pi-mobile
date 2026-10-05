package ru.billyhargrove.pimobile.ui;

import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

/** Spatial motion is spring based; never reanimate a streaming text update. */
public final class ExpressiveMotion {
    private ExpressiveMotion() {}
    public static boolean enabled() { return ValueAnimator.areAnimatorsEnabled(); }

    public static void enter(View view, int delay) {
        if (!enabled()) return;
        view.setAlpha(0f);
        view.setTranslationY(18 * view.getResources().getDisplayMetrics().density);
        view.animate().alpha(1f).translationY(0).setStartDelay(delay).setDuration(380)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .withEndAction(() -> view.animate().setStartDelay(0)).start();
    }

    /** Friction-led panel entrance: separate opacity and spatial springs. */
    public static void reveal(View view) {
        if (!enabled()) return;
        view.setAlpha(0f); view.setScaleX(.94f); view.setScaleY(.94f);
        view.setTranslationY(14 * view.getResources().getDisplayMetrics().density);
        view.animate().alpha(1f).setDuration(180).start();
        SpringAnimation x = new SpringAnimation(view, SpringAnimation.SCALE_X, 1f);
        SpringAnimation y = new SpringAnimation(view, SpringAnimation.SCALE_Y, 1f);
        SpringAnimation lift = new SpringAnimation(view, SpringAnimation.TRANSLATION_Y, 0f);
        for (SpringAnimation animation : new SpringAnimation[]{x, y, lift}) {
            animation.getSpring().setDampingRatio(.78f).setStiffness(420); animation.start();
        }
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {}
            public void onViewDetachedFromWindow(View v) { x.cancel(); y.cancel(); lift.cancel(); v.animate().cancel(); v.setScaleX(1); v.setScaleY(1); v.setAlpha(1); v.setTranslationY(0); v.removeOnAttachStateChangeListener(this); }
        });
    }

    /** Does not consume events: click, scrolling, and accessibility remain native. */
    public static void press(View view) {
        if (view instanceof com.google.android.material.button.MaterialButton) {
            com.google.android.material.button.MaterialButton button = (com.google.android.material.button.MaterialButton) view;
            button.setShapeAppearanceModel(button.getShapeAppearanceModel());
        }
        SpringAnimation x = new SpringAnimation(view, SpringAnimation.SCALE_X);
        SpringAnimation y = new SpringAnimation(view, SpringAnimation.SCALE_Y);
        x.setSpring(new SpringForce(1).setDampingRatio(.66f).setStiffness(500));
        y.setSpring(new SpringForce(1).setDampingRatio(.66f).setStiffness(500));
        view.setOnTouchListener((v, event) -> {
            if (!enabled() || !v.isEnabled()) return false;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                x.animateToFinalPosition(.90f); y.animateToFinalPosition(.90f);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                x.animateToFinalPosition(1f); y.animateToFinalPosition(1f);
            }
            return false;
        });
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {}
            public void onViewDetachedFromWindow(View v) {
                x.cancel(); y.cancel(); v.setScaleX(1); v.setScaleY(1);
            }
        });
    }

    public static void buttons(View view) {
        if (view instanceof android.widget.Button) press(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i=0;i<group.getChildCount();i++) buttons(group.getChildAt(i));
        }
    }

    public static void list(RecyclerView list) {
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setSupportsChangeAnimations(false);
        animator.setAddDuration(enabled() ? 260 : 0);
        animator.setRemoveDuration(enabled() ? 180 : 0);
        animator.setMoveDuration(enabled() ? 320 : 0);
        list.setItemAnimator(animator);
    }
}
