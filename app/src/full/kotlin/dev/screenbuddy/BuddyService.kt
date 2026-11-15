package dev.screenbuddy

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager

/**
 * Hosts the cat in an accessibility overlay window and feeds it the position of the user's taps.
 * Only event types and on-screen bounds are used; no screen content is read or kept.
 */
class BuddyService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var host: CatHost? = null
    private val bounds = Rect()
    private val handler = Handler(Looper.getMainLooper())

    /** Window changes arrive in bursts (a tray pull sends many); look at the screen once per burst. */
    private val checkWindows = Runnable {
        val cat = host ?: return@Runnable
        cat.applyWorld()
        shadeOpen = isShadeOpen()
        cat.setBall(Prefs.ball(Prefs.of(this)) && shadeOpen)
        updateCalm()
    }

    /** The app the user is looking at, as far as we can tell from window changes. */
    private var foreground: String? = null
    private var shadeOpen = false
    private var launcher: String? = null
    private var keyboards: Set<String> = emptySet()

    override fun onServiceConnected() {
        launcher = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
        keyboards = getSystemService(InputMethodManager::class.java).inputMethodList.map { it.packageName }.toSet()
        Prefs.of(this).registerOnSharedPreferenceChangeListener(this)
        host = CatHost(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, ::keyboardTop)
            .also { it.start() }
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences, key: String?) {
        if (key == Prefs.CALM_MODE || key == Prefs.CALM_APPS) updateCalm()
    }

    /** Calm her down while a demanding app is open, unless the tray is pulled down over it. */
    private fun updateCalm() {
        val cat = host ?: return
        val app = foreground
        val prefs = Prefs.of(this)
        val busyApp = when (Prefs.calmMode(prefs)) {
            Prefs.CALM_ALL -> app != null && app != launcher && app != packageName
            Prefs.CALM_LISTED -> app != null && app in Prefs.calmApps(prefs)
            else -> false
        }
        cat.calm = busyApp && !shadeOpen
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkWindows)
        Prefs.of(this).unregisterOnSharedPreferenceChangeListener(this)
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
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // a new screen or app came up; ignore the system UI and keyboards, which float over apps
            val pkg = event.packageName?.toString()
            if (pkg != null && pkg != "com.android.systemui" && pkg !in keyboards && pkg != foreground) {
                foreground = pkg
                updateCalm()
            }
        }
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
