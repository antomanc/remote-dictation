package dev.remotedictation

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/** A small banner at the top of the screen; safe to call from any thread. */
class StatusOverlay(private val service: AccessibilityService) {

    enum class Style(val color: Int) {
        LISTENING(0xE6_1E_6B_52.toInt()),
        INFO(0xE6_30_30_30.toInt()),
        ERROR(0xE6_8B_1E_1E.toInt()),
    }

    private val main = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val hideRunnable = Runnable { hideNow() }
    private var view: TextView? = null

    fun show(text: String, style: Style, hideAfterMillis: Long = 0) {
        main.post {
            val banner = view ?: createView().also {
                windowManager.addView(it, layoutParams())
                view = it
            }
            banner.text = text
            (banner.background as GradientDrawable).setColor(style.color)
            main.removeCallbacks(hideRunnable)
            if (hideAfterMillis > 0) main.postDelayed(hideRunnable, hideAfterMillis)
        }
    }

    fun hide() {
        main.post { hideNow() }
    }

    private fun hideNow() {
        main.removeCallbacks(hideRunnable)
        view?.let(windowManager::removeView)
        view = null
    }

    private fun createView() = TextView(service).apply {
        setTextColor(0xFF_FF_FF_FF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        maxLines = 3
        val padding = dp(16)
        setPadding(padding * 2, padding, padding * 2, padding)
        background = GradientDrawable().apply { cornerRadius = dp(28).toFloat() }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = dp(32)
    }

    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()
}
