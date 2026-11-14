package dev.screenbuddy

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

/**
 * Finds the top of the on-screen keyboard without an accessibility service.
 *
 * Android has no API for this, so we add an invisible one-pixel-wide window that asks to be
 * resized around the keyboard and watch how far up its bottom edge moves. This works on most
 * phones but is not guaranteed; when it fails, [top] simply stays null.
 */
class KeyboardWatcher(context: Context, private val onChange: () -> Unit) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private val metrics = context.resources.displayMetrics
    private val view = View(context)
    private val location = IntArray(2)
    private var attached = false

    /** Top edge of the keyboard in screen pixels, or null while it is closed or undetected. */
    var top: Int? = null
        private set

    fun start() {
        val lp = WindowManager.LayoutParams(
            1, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_FOCUSABLE + ALT_FOCUSABLE_IM together mean "lay me out around the keyboard"
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSPARENT,
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.alpha = 0f
        @Suppress("DEPRECATION")
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        if (Build.VERSION.SDK_INT >= 30) {
            lp.fitInsetsTypes = lp.fitInsetsTypes or WindowInsets.Type.ime()
        }
        lp.title = "Screen Buddy keyboard watcher"
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> measure() }
        view.setOnApplyWindowInsetsListener { _, insets ->
            view.post { measure() }
            insets
        }
        wm.addView(view, lp)
        attached = true
    }

    fun stop() {
        if (attached) wm.removeView(view)
        attached = false
    }

    private fun measure() {
        if (!attached) return
        @Suppress("DEPRECATION")
        val screenH = if (Build.VERSION.SDK_INT >= 30) {
            wm.currentWindowMetrics.bounds.height()
        } else {
            metrics.heightPixels
        }
        var found: Int? = null
        // 1. the window itself was shortened to make room for the keyboard
        view.getLocationOnScreen(location)
        val bottom = location[1] + view.height
        if (view.height > 0 && bottom < screenH * 0.85f) found = bottom
        // 2. or the system reports the keyboard as an inset
        if (found == null && Build.VERSION.SDK_INT >= 30) {
            val ime = view.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0
            if (ime > screenH * 0.15f) found = screenH - ime
        }
        if (found != top) {
            top = found
            onChange()
        }
    }
}
