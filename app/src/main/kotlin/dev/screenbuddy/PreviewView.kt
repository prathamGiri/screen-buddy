package dev.screenbuddy

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import dev.screenbuddy.core.Anim
import dev.screenbuddy.core.Behavior
import dev.screenbuddy.core.Sprites
import kotlin.math.min

/**
 * The companion on a little lit stage in the app: she cycles through the behaviours that are
 * switched on, and purrs when tapped.
 */
@SuppressLint("ViewConstructor")
class PreviewView(context: Context, private val onCaption: (String) -> Unit) : View(context) {

    private class Act(val anim: Anim, val caption: String, val seconds: Float, val needs: Set<Behavior> = emptySet())

    // the same order as on screen: grooming and scratching now and then, a yawn before a nap,
    // and a stretch once she wakes up
    private val acts = listOf(
        Act(Anim.IDLE, "Watching you", 3.5f),
        Act(Anim.LICK, "Licking her paw", 2.5f, setOf(Behavior.GROOM)),
        Act(Anim.WASH, "Washing her face", 2f, setOf(Behavior.GROOM)),
        Act(Anim.IDLE, "Watching you", 3f),
        Act(Anim.SCRATCH, "Scratching an ear", 1.8f, setOf(Behavior.SCRATCH)),
        Act(Anim.IDLE, "Getting sleepy", 2.5f, setOf(Behavior.NAP)),
        Act(Anim.YAWN, "Yawning", 2.4f, setOf(Behavior.YAWN)),
        Act(Anim.SLEEP, "Napping", 4.5f, setOf(Behavior.NAP)),
        Act(Anim.STRETCH, "Waking with a stretch", 3.4f, setOf(Behavior.NAP, Behavior.STRETCH)),
        Act(Anim.IDLE, "Watching you", 3f),
        Act(Anim.WATCH, "Eyeing a ball of wool", 2.5f, setOf(Behavior.BALL)),
        Act(Anim.SWAT, "Batting the ball", 1.2f, setOf(Behavior.BALL)),
    )
    private val pet = Act(Anim.PET, "Purring", 1.8f)

    /** The behaviours switched on; the preview only shows those. */
    var enabled: Set<Behavior> = Behavior.entries.toSet()
        set(value) {
            field = value
            if (!value.containsAll(current.needs)) next()
        }

    private var index = 0
    private var current = acts[0]
    private var startedAt = SystemClock.uptimeMillis()
    private var shownBitmap: Bitmap? = null

    private val bitmaps = HashMap<Int, Bitmap>()
    private val sprite = Paint().apply { isFilterBitmap = false }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dst = Rect()
    private val oval = RectF()

    private val tick = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            val elapsed = (SystemClock.uptimeMillis() - startedAt) / 1000f
            if (elapsed >= current.seconds) next()
            val b = bitmapFor(current.anim, frameAt((SystemClock.uptimeMillis() - startedAt) / 1000f))
            if (b !== shownBitmap) {
                shownBitmap = b
                invalidate()
            }
            postDelayed(this, 50)
        }
    }

    private fun frameAt(t: Float): Int {
        val a = current.anim
        val f = (t * a.fps).toInt()
        return if (a.loops) f % a.frames else f.coerceAtMost(a.frames - 1)
    }

    private fun next() {
        var tries = 0
        do {
            index = (index + 1) % acts.size
            tries++
        } while (!enabled.containsAll(acts[index].needs) && tries <= acts.size)
        play(acts[index])
    }

    private fun play(act: Act) {
        current = act
        startedAt = SystemClock.uptimeMillis()
        onCaption(act.caption)
    }

    private fun bitmapFor(anim: Anim, frame: Int): Bitmap = bitmaps.getOrPut(anim.ordinal * 64 + frame) {
        Bitmap.createBitmap(Sprites.frame(anim, frame), Sprites.SIZE, Sprites.SIZE, Bitmap.Config.ARGB_8888)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        play(current)
        post(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        shadow.shader = RadialGradient(
            w / 2f, h * 0.86f, w * 0.22f,
            intArrayOf(0x66000000, 0x00000000), null, Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val b = shownBitmap ?: return
        // whole sprite pixels only, so the pixel art stays crisp
        val scale = (min(width, height) * 0.62f / Sprites.SIZE).toInt().coerceAtLeast(1)
        val side = Sprites.SIZE * scale
        val left = (width - side) / 2
        val floor = (height * 0.86f).toInt()
        oval.set(width * 0.28f, floor - height * 0.05f, width * 0.72f, floor + height * 0.05f)
        canvas.drawOval(oval, shadow)
        // sprites stand on their bottom row of pixels, two pixels above the frame edge
        val top = floor - side + 2 * scale
        dst.set(left, top, left + side, top + side)
        canvas.drawBitmap(b, null, dst, sprite)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            index = 0
            play(pet)
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
