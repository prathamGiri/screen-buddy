package dev.screenbuddy

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/** Draws one pixel-art sprite and turns touches on it into taps, drags and flings. */
@SuppressLint("ViewConstructor")
class SpriteView(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        fun onTap()
        fun onDragStart()
        fun onDragTo(x: Float, y: Float)
        fun onDragEnd(velX: Float, velY: Float)
        fun onOutsideTouch()
    }

    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val dst = Rect()
    private var bitmap: Bitmap? = null
    private var flip = false

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var tracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var grabX = 0f
    private var grabY = 0f
    private var dragging = false

    /** Top-left of the window on screen, kept up to date by the service. */
    var windowX = 0
    var windowY = 0

    /** Show [next], mirrored when facing left. Only redraws when the picture actually changes. */
    fun show(next: Bitmap, facingRight: Boolean) {
        if (next !== bitmap || flip == facingRight) {
            bitmap = next
            flip = !facingRight
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        dst.set(0, 0, width, height)
        if (flip) {
            canvas.save()
            canvas.scale(-1f, 1f, width / 2f, 0f)
            canvas.drawBitmap(b, null, dst, paint)
            canvas.restore()
        } else {
            canvas.drawBitmap(b, null, dst, paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                listener.onOutsideTouch()
                return false
            }
            MotionEvent.ACTION_DOWN -> {
                tracker = VelocityTracker.obtain()
                downX = e.rawX
                downY = e.rawY
                grabX = e.rawX - windowX
                grabY = e.rawY - windowY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && (abs(e.rawX - downX) > slop || abs(e.rawY - downY) > slop)) {
                    dragging = true
                    listener.onDragStart()
                }
                if (dragging) listener.onDragTo(e.rawX - grabX, e.rawY - grabY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val t = tracker
                if (dragging) {
                    track(e)
                    t?.computeCurrentVelocity(1000)
                    listener.onDragEnd(t?.xVelocity ?: 0f, t?.yVelocity ?: 0f)
                } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                    listener.onTap()
                }
                t?.recycle()
                tracker = null
                dragging = false
                return true
            }
        }
        track(e)
        return true
    }

    /** The window moves under the finger while dragging, so track velocity in screen coordinates. */
    private fun track(e: MotionEvent) {
        val t = tracker ?: return
        val raw = MotionEvent.obtain(e)
        raw.setLocation(e.rawX, e.rawY)
        t.addMovement(raw)
        raw.recycle()
    }
}
