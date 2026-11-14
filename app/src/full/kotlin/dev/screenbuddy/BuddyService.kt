package dev.screenbuddy

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.Rect
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

    override fun onServiceConnected() {
        host = CatHost(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, ::keyboardTop)
            .also { it.start() }
    }

    override fun onDestroy() {
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
                }
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> cat.applyWorld()
            else -> cat.onNotice()
        }
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
