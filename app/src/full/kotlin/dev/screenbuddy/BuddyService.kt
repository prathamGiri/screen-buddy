package dev.screenbuddy

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Hosts the cat in an accessibility overlay window and feeds it the position of the user's taps.
 * Only event types and on-screen bounds are used; no screen content is read or kept.
 */
class BuddyService : AccessibilityService() {

    private var host: CatHost? = null
    private val bounds = Rect()
    private val handler = Handler(Looper.getMainLooper())

    /** Window changes arrive in bursts (a tray pull sends many); look at the screen once per burst. */
    private val checkWindows = Runnable {
        val cat = host ?: return@Runnable
        cat.applyWorld()
        cat.setBall(Prefs.ball(Prefs.of(this)) && isShadeOpen())
    }

    override fun onServiceConnected() {
        host = CatHost(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, ::keyboardTop)
            .also { it.start() }
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkWindows)
        host?.stop()
        host = null
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        host?.applyWorld()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val cat = host ?: return
        if (event.packageName == packageName) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED, AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                val source = event.source
                if (source == null) {
                    cat.onNotice()
                } else {
                    source.getBoundsInScreen(bounds)
                    // a "click" on something that fills the screen says nothing about where the finger was
                    val vague = bounds.isEmpty || bounds.height() > cat.floor * 0.5f
                    if (vague) cat.onNotice() else cat.onTap(bounds.exactCenterX(), bounds.exactCenterY())
                    @Suppress("DEPRECATION")
                    if (Build.VERSION.SDK_INT < 33) source.recycle()
                }
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handler.removeCallbacks(checkWindows)
                handler.postDelayed(checkWindows, 80L)
            }
        }
    }

    /**
     * True while the notification tray is pulled down. There is no direct API for this, so look
     * for a system window that covers most of the screen while the phone is unlocked.
     */
    private fun isShadeOpen(): Boolean {
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return false
        val metrics = resources.displayMetrics
        try {
            for (w in windows) {
                if (w.type != AccessibilityWindowInfo.TYPE_SYSTEM) continue
                w.getBoundsInScreen(bounds)
                if (bounds.height() > metrics.heightPixels * 0.5f && bounds.width() > metrics.widthPixels * 0.9f) {
                    return true
                }
            }
        } catch (_: RuntimeException) {
            // the window list is occasionally unavailable mid-transition
        }
        return false
    }

    private fun keyboardTop(): Int? {
        try {
            for (w in windows) {
                if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    w.getBoundsInScreen(bounds)
                    return bounds.top
                }
            }
        } catch (_: RuntimeException) {
            // the window list is occasionally unavailable mid-transition
        }
        return null
    }
}
