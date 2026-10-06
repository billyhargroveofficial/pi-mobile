package ru.billyhargrove.pimobile.ui

import android.animation.ValueAnimator
import android.view.*
import android.view.animation.PathInterpolator
import androidx.dynamicanimation.animation.*
import androidx.recyclerview.widget.*

/** Native interop motion. Streaming text updates never restart an animation. */
object ExpressiveMotion {
    @JvmStatic fun enabled() = ValueAnimator.areAnimatorsEnabled()
    @JvmStatic fun enter(view: View, delay: Int) {
        if (!enabled()) return
        view.alpha = 0f; view.translationY = 18 * view.resources.displayMetrics.density
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay.toLong()).setDuration(380)
            .setInterpolator(PathInterpolator(.2f, 0f, 0f, 1f)).withEndAction { view.animate().startDelay = 0 }.start()
    }
    @JvmStatic fun reveal(view: View) {
        if (!enabled()) return
        view.alpha = 0f; view.scaleX = .94f; view.scaleY = .94f; view.translationY = 14 * view.resources.displayMetrics.density
        view.animate().alpha(1f).setDuration(180).start()
        val springs = listOf(SpringAnimation(view, SpringAnimation.SCALE_X, 1f), SpringAnimation(view, SpringAnimation.SCALE_Y, 1f),
            SpringAnimation(view, SpringAnimation.TRANSLATION_Y, 0f))
        springs.forEach { it.spring.setDampingRatio(.78f).setStiffness(420f); it.start() }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                springs.forEach { it.cancel() }; v.animate().cancel(); v.scaleX = 1f; v.scaleY = 1f; v.alpha = 1f; v.translationY = 0f
                v.removeOnAttachStateChangeListener(this)
            }
        })
    }
    @JvmStatic fun press(view: View) {
        val x = SpringAnimation(view, SpringAnimation.SCALE_X).setSpring(SpringForce(1f).setDampingRatio(.66f).setStiffness(500f))
        val y = SpringAnimation(view, SpringAnimation.SCALE_Y).setSpring(SpringForce(1f).setDampingRatio(.66f).setStiffness(500f))
        view.setOnTouchListener { v, event ->
            if (enabled() && v.isEnabled) when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x.animateToFinalPosition(.9f); y.animateToFinalPosition(.9f) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { x.animateToFinalPosition(1f); y.animateToFinalPosition(1f) }
            }
            false
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { x.cancel(); y.cancel(); v.scaleX = 1f; v.scaleY = 1f }
        })
    }
    @JvmStatic fun buttons(view: View) { if (view is android.widget.Button) press(view); if (view is ViewGroup) for (i in 0 until view.childCount) buttons(view.getChildAt(i)) }
    @JvmStatic fun list(list: RecyclerView) { list.itemAnimator = DefaultItemAnimator().apply {
        supportsChangeAnimations = false; addDuration = if (enabled()) 260 else 0; removeDuration = if (enabled()) 180 else 0; moveDuration = if (enabled()) 320 else 0
    } }
}
