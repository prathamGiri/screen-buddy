package dev.screenbuddy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.PowerManager
import android.view.Choreographer
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.WindowInsets
import android.view.WindowManager
import dev.screenbuddy.core.Anim
import dev.screenbuddy.core.Behavior
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
) : SharedPreferences.OnSharedPreferenceChangeListener, SpriteView.Listener, Choreographer.FrameCallback {

    private val prefs = Prefs.of(context)
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: SpriteView? = null
    private var params: WindowManager.LayoutParams? = null
    private var ballView: SpriteView? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var brain: CatBrain? = null

    // Every sprite frame as a ready-made bitmap, so drawing a frame allocates nothing.
    private val catBitmaps = arrayOfNulls<Bitmap>(Anim.entries.size * FRAME_SLOTS)
    private val ballBitmaps = arrayOfNulls<Bitmap>(Sprites.BALL_FRAMES)

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
        val lp = windowParams(size, WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
        val screen = screenSize()
        val cat = CatBrain(screen.x.toFloat(), findFloor(screen).toFloat(), size.toFloat())
        // without an accessibility service we only know the screen was touched, not where
        cat.chaseEnabled = false
        cat.enabled = Prefs.behaviors(prefs)
        // drop in from above the floor
        cat.dropIn(size * 3f)
        val v = SpriteView(context, this)
        lp.x = cat.x.roundToInt()
        lp.y = cat.y.roundToInt()
        wm.addView(v, lp)
        view = v
        params = lp
        brain = cat
        render()
        kick()
    }

    private fun windowParams(side: Int, extraFlags: Int = 0): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            side, side,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                extraFlags,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        lp.title = "Screen Buddy"
        return lp
    }

    private fun hideCat() {
        ballView?.let { wm.removeView(it) }
        ballView = null
        ballParams = null
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
        cat.enabled = Prefs.behaviors(prefs)
        cat.resize(screen.x.toFloat(), findFloor(screen).toFloat(), size.toFloat())
        if (lp.width != size) {
            lp.width = size
            lp.height = size
            wm.updateViewLayout(view, lp)
            // the ball is sized to match the cat; drop a fresh one if there was one
            if (cat.ball != null) {
                cat.takeBall()
                cat.giveBall()
            }
        }
        render()
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
        val top = if (Prefs.isOn(prefs, Behavior.KEYBOARD)) keyboardTop() else null
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
        // While moving, update on every display frame so motion stays smooth. While sitting, a
        // few checks a second keep the idle animation on time; asleep, she needs even fewer.
        if (cat.isBusy) {
            Choreographer.getInstance().postFrameCallback(this)
        } else {
            // fast enough to show every frame of the current animation on time
            val delay = (500f / cat.anim.fps).toLong().coerceIn(40L, 250L)
            Choreographer.getInstance().postFrameCallbackDelayed(this, delay)
        }
    }

    private fun catBitmap(anim: Anim, frame: Int): Bitmap {
        val i = anim.ordinal * FRAME_SLOTS + frame
        return catBitmaps[i] ?: Bitmap.createBitmap(
            Sprites.frame(anim, frame), Sprites.SIZE, Sprites.SIZE, Bitmap.Config.ARGB_8888,
        ).also { catBitmaps[i] = it }
    }

    private fun ballBitmap(frame: Int): Bitmap =
        ballBitmaps[frame] ?: Bitmap.createBitmap(
            Sprites.ball(frame), Sprites.BALL_SIZE, Sprites.BALL_SIZE, Bitmap.Config.ARGB_8888,
        ).also { ballBitmaps[frame] = it }

    private fun render() {
        val cat = brain ?: return
        val v = view ?: return
        val lp = params ?: return
        v.show(catBitmap(cat.anim, cat.frame), cat.facingRight)
        val nx = cat.x.roundToInt()
        val ny = cat.y.roundToInt()
        v.windowX = nx
        v.windowY = ny
        if (nx != lp.x || ny != lp.y) {
            lp.x = nx
            lp.y = ny
            wm.updateViewLayout(v, lp)
        }
        renderBall(cat)
    }

    private fun renderBall(cat: CatBrain) {
        val ball = cat.ball
        var bv = ballView
        if (ball == null) {
            if (bv != null) {
                wm.removeView(bv)
                ballView = null
                ballParams = null
            }
            return
        }
        val bx = ball.x.roundToInt()
        val by = ball.y.roundToInt()
        var lp = ballParams
        if (bv == null || lp == null) {
            bv = SpriteView(context, ballListener)
            lp = windowParams(ball.d.roundToInt())
            lp.x = bx
            lp.y = by
            wm.addView(bv, lp)
            ballView = bv
            ballParams = lp
        }
        bv.show(ballBitmap(ball.frame), true)
        bv.windowX = bx
        bv.windowY = by
        if (bx != lp.x || by != lp.y) {
            lp.x = bx
            lp.y = by
            wm.updateViewLayout(bv, lp)
        }
    }

    /** Touches on the ball: tap to nudge it, drag to carry it, fling to throw it. */
    private val ballListener = object : SpriteView.Listener {
        override fun onTap() {
            brain?.ballPoke()
            kick()
        }

        override fun onDragStart() {
            brain?.ballGrab()
            kick()
        }

        override fun onDragTo(x: Float, y: Float) {
            brain?.ballDragTo(x, y)
        }

        override fun onDragEnd(velX: Float, velY: Float) {
            brain?.ballRelease(velX, velY)
            kick()
        }

        override fun onOutsideTouch() = Unit
    }

    // --- touches on the cat itself ---

    override fun onTap() {
        brain?.onPet()
        view?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
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

    private companion object {
        /** Room for up to this many frames per animation in [catBitmaps]. */
        const val FRAME_SLOTS = 16
    }
}
