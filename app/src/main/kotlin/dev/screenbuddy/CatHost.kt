package dev.screenbuddy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.PowerManager
import android.view.Choreographer
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import dev.screenbuddy.core.CatBrain
import dev.screenbuddy.core.Sprites
import kotlin.math.roundToInt

/**
 * Owns the cat's overlay window, its behaviour and the frame loop. The surrounding service
 * decides which kind of overlay window to use and feeds in whatever it knows about touches.
 *
 * @param windowType the WindowManager.LayoutParams window type to draw the cat in
 * @param keyboardTop returns the top edge of the on-screen keyboard, or null if unknown or closed
 */
class CatHost(
    private val context: Context,
    private val windowType: Int,
    private val keyboardTop: () -> Int? = { null },
) : SharedPreferences.OnSharedPreferenceChangeListener, CatView.Listener, Choreographer.FrameCallback {

    private val prefs = Prefs.of(context)
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: CatView? = null
    private var params: WindowManager.LayoutParams? = null
    private var brain: CatBrain? = null

    private var screenOn = true
    private var ticking = false
    private var lastFrameNanos = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn = intent.action != Intent.ACTION_SCREEN_OFF
            if (screenOn) kick()
        }
    }

    fun start() {
        prefs.registerOnSharedPreferenceChangeListener(this)
        context.registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
        screenOn = context.getSystemService(PowerManager::class.java).isInteractive
        if (Prefs.show(prefs)) showCat()
    }

    fun stop() {
        hideCat()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        context.unregisterReceiver(screenReceiver)
    }

    /** The floor the cat currently stands on, in screen pixels. */
    val floor: Float get() = brain?.floor ?: 0f

    fun onTap(x: Float, y: Float) {
        brain?.onTap(x, y)
        kick()
    }

    fun onNotice() {
        brain?.onNotice()
        kick()
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences, key: String?) {
        if (Prefs.show(p)) {
            if (view == null) showCat() else applyWorld()
        } else {
            hideCat()
        }
    }

    // --- overlay window ---

    private fun showCat() {
        if (view != null) return
        val size = spriteSize()
        val lp = WindowManager.LayoutParams(
            size, size,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        lp.title = "Screen Buddy"
        val screen = screenSize()
        val cat = CatBrain(screen.x.toFloat(), findFloor(screen).toFloat(), size.toFloat())
        cat.chaseEnabled = Prefs.chase(prefs)
        // drop in from above the floor
        cat.dragStart()
        cat.dragTo(cat.x, cat.floor - size * 4f)
        cat.dragEnd(0f, 0f)
        val v = CatView(context, this)
        lp.x = cat.x.roundToInt()
        lp.y = cat.y.roundToInt()
        wm.addView(v, lp)
        view = v
        params = lp
        brain = cat
        render()
        kick()
    }

    private fun hideCat() {
        view?.let { wm.removeView(it) }
        view = null
        params = null
        brain = null
    }

    /** Re-measure the screen, floor and sprite size after a rotation, keyboard or settings change. */
    fun applyWorld() {
        val cat = brain ?: return
        val lp = params ?: return
        val size = spriteSize()
        val screen = screenSize()
        cat.chaseEnabled = Prefs.chase(prefs)
        cat.resize(screen.x.toFloat(), findFloor(screen).toFloat(), size.toFloat())
        if (lp.width != size) {
            lp.width = size
            lp.height = size
            wm.updateViewLayout(view, lp)
        }
        kick()
    }

    private fun spriteSize(): Int =
        (Sprites.SIZE * Prefs.scale(prefs) * context.resources.displayMetrics.density).roundToInt()

    @Suppress("DEPRECATION")
    private fun screenSize(): Point {
        val p = Point()
        if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            p.set(b.width(), b.height())
        } else {
            wm.defaultDisplay.getRealSize(p)
        }
        return p
    }

    /** The cat stands on the navigation bar, or on top of the keyboard while it is open. */
    private fun findFloor(screen: Point): Int {
        var floor = screen.y
        if (Build.VERSION.SDK_INT >= 30) {
            floor -= wm.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
        }
        val top = keyboardTop()
        if (top != null && top > screen.y / 4 && top < floor) floor = top
        return floor
    }

    // --- frame loop ---

    private fun kick() {
        if (ticking || view == null || !screenOn) return
        ticking = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        ticking = false
        val cat = brain ?: return
        if (!screenOn) return
        val dt = if (lastFrameNanos == 0L) 1f / 60f else (frameTimeNanos - lastFrameNanos) / 1e9f
        // idle animations advance in real time even though they tick slowly
        var remaining = dt.coerceAtMost(0.5f)
        while (remaining > 0f) {
            val step = remaining.coerceAtMost(0.05f)
            cat.update(step)
            remaining -= step
        }
        render()
        ticking = true
        lastFrameNanos = frameTimeNanos
        if (cat.isBusy) {
            Choreographer.getInstance().postFrameCallback(this)
        } else {
            Choreographer.getInstance().postFrameCallbackDelayed(this, 120L)
        }
    }

    private fun render() {
        val cat = brain ?: return
        val v = view ?: return
        val lp = params ?: return
        v.show(cat.anim, cat.frame, cat.facingRight)
        val nx = cat.x.roundToInt()
        val ny = cat.y.roundToInt()
        v.windowX = nx
        v.windowY = ny
        if (nx != lp.x || ny != lp.y) {
            lp.x = nx
            lp.y = ny
            wm.updateViewLayout(v, lp)
        }
    }

    // --- touches on the cat itself ---

    override fun onPet() {
        brain?.onPet()
        kick()
    }

    override fun onDragStart() {
        brain?.dragStart()
        kick()
    }

    override fun onDragTo(x: Float, y: Float) {
        brain?.dragTo(x, y)
    }

    override fun onDragEnd(velX: Float, velY: Float) {
        brain?.dragEnd(velX, velY)
        kick()
    }

    override fun onOutsideTouch() = onNotice()
}
