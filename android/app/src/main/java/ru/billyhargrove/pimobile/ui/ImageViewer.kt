package ru.billyhargrove.pimobile.ui

import android.app.Dialog
import android.content.*
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.*
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.*
import com.google.android.material.button.MaterialButton
import ru.billyhargrove.pimobile.PiApp
import ru.billyhargrove.pimobile.R
import ru.billyhargrove.pimobile.net.MediaLoader

/** Fullscreen authenticated bitmap leaf; pinch/pan/double tap, never arbitrary file URLs. */
class ImageViewer : AppCompatDialogFragment() {
    private var localBitmap: Bitmap? = null
    companion object {
        @JvmStatic fun show(context: Context, url: String?, local: Bitmap?) {
            var host = context
            while (host !is AppCompatActivity && host is ContextWrapper && host.baseContext !== host) host = host.baseContext
            if (host !is AppCompatActivity) return
            val currentScope = PiApp.get(context).settings().connectionScope()
            val sourceScope = host.intent.getStringExtra("connection_scope") ?: currentScope
            if (sourceScope != currentScope) return // A still-visible old window must not load its URL with new credentials.
            ImageViewer().apply { arguments = Bundle().apply { putString("url", url.orEmpty()); putString("connection_scope", sourceScope) }; localBitmap = local }
                .show(host.supportFragmentManager, "image-viewer")
        }
    }
    override fun onCreateDialog(state: Bundle?): Dialog {
        val context = requireContext(); val dialog = Dialog(context, R.style.Theme_PiMobile)
        val root = FrameLayout(context).apply { setBackgroundColor(context.getColor(R.color.bg)) }
        val image = ZoomImage(context).apply { id = R.id.zoomImage; contentDescription = "Image. Pinch or double-tap to zoom" }
        root.addView(image, FrameLayout.LayoutParams(-1, -1))
        val error = TextView(context).apply { gravity = Gravity.CENTER; setTextColor(context.getColor(R.color.danger)); visibility = View.GONE }
        root.addView(error, FrameLayout.LayoutParams(-1, -1))
        val close = LayoutInflater.from(context).inflate(R.layout.icon_button, root, false) as MaterialButton
        close.id = R.id.closeImageButton; close.setIconResource(R.drawable.ic_back); close.contentDescription = "Close image"
        close.setOnClickListener { dismiss() }; ExpressiveMotion.press(close)
        val size = (48 * resources.displayMetrics.density).toInt()
        root.addView(close, FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.START).apply { setMargins(size / 3, size / 3, 0, 0) })
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets -> val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()); view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets }
        dialog.setContentView(root)
        val expectedScope = requireArguments().getString("connection_scope")
        if ((expectedScope == null && PiApp.get(context).settings().profiles().size > 1) ||
            (expectedScope != null && expectedScope != PiApp.get(context).settings().connectionScope())) {
            error.text = "Computer changed. Open the image again from its computer."; error.visibility = View.VISIBLE
            return dialog
        }
        localBitmap?.let(image::setImageBitmap) ?: PiApp.get(context).mediaLoader().load(requireArguments().getString("url", ""), object : MediaLoader.Callback {
            override fun onLoaded(resolvedUrl: String, bitmap: Bitmap) { if (isAdded) image.setImageBitmap(bitmap) }
            override fun onFailed(resolvedUrl: String, message: String) { if (isAdded) { error.text = message; error.visibility = View.VISIBLE } }
        })
        return dialog
    }
    override fun onStart() { super.onStart(); dialog?.window?.setLayout(-1, -1) }
    internal class ZoomImage(context: Context) : AppCompatImageView(context) {
        private val transform = Matrix()
        private var fit = 1f; private var scale = 1f; private var lastX = 0f; private var lastY = 0f; private var moved = false
        private val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean { zoom(detector.scaleFactor, detector.focusX, detector.focusY); return true }
        })
        private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent) = true
            override fun onDoubleTap(event: MotionEvent): Boolean { if (scale > fit * 1.1f) reset() else zoom(2.5f, event.x, event.y); return true }
        })
        init { scaleType = ScaleType.MATRIX; isClickable = true }
        override fun setImageBitmap(bitmap: Bitmap?) { super.setImageBitmap(bitmap); post(::reset) }
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); reset() }
        private fun reset() {
            val image = drawable ?: return; if (width == 0 || image.intrinsicWidth <= 0 || image.intrinsicHeight <= 0) return
            val w = image.intrinsicWidth.toFloat(); val h = image.intrinsicHeight.toFloat()
            fit = minOf(width / w, height / h); scale = fit; transform.reset(); transform.postScale(fit, fit)
            transform.postTranslate((width - w * fit) / 2, (height - h * fit) / 2); imageMatrix = transform
        }
        private fun zoom(factor: Float, x: Float, y: Float) { val next = (scale * factor).coerceIn(fit, fit * 6); transform.postScale(next / scale, next / scale, x, y); scale = next; bound() }
        private fun bound() {
            val image = drawable ?: return
            val rect = RectF(0f, 0f, image.intrinsicWidth.toFloat(), image.intrinsicHeight.toFloat()); transform.mapRect(rect)
            fun delta(size: Float, start: Float, end: Float, viewport: Int) = when {
                size <= viewport -> (viewport - size) / 2 - start; start > 0 -> -start; end < viewport -> viewport - end; else -> 0f
            }
            transform.postTranslate(delta(rect.width(), rect.left, rect.right, width), delta(rect.height(), rect.top, rect.bottom, height)); imageMatrix = transform
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            pinch.onTouchEvent(event); taps.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    if (!pinch.isInProgress && event.pointerCount == 1) { val dx = event.x - lastX; val dy = event.y - lastY
                        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 3) moved = true; transform.postTranslate(dx, dy); bound() }
                    lastX = event.x; lastY = event.y
                }
                MotionEvent.ACTION_UP -> if (!moved) performClick()
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }
}
