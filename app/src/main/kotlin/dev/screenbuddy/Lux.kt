package dev.screenbuddy

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.TextView

/** The app's look: warm near-black surfaces, ivory type and a champagne-gold accent. */
object Lux {
    const val INK = 0xFF0F0D0B.toInt()
    const val SURFACE = 0xFF191613.toInt()
    const val SURFACE_HI = 0xFF221E1A.toInt()
    const val HAIRLINE = 0xFF332C25.toInt()
    const val GOLD = 0xFFD4B06A.toInt()
    const val GOLD_DEEP = 0xFF9C7F46.toInt()
    const val IVORY = 0xFFF3ECE0.toInt()
    const val MUTED = 0xFFA0968A.toInt()
    const val FAINT = 0xFF6A6259.toInt()
    const val GLOW = 0xFF3B2F22.toInt()

    val SERIF: Typeface = Typeface.create("serif", Typeface.NORMAL)
    val SERIF_ITALIC: Typeface = Typeface.create("serif", Typeface.ITALIC)
    val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun dp(context: Context, v: Float) = v * context.resources.displayMetrics.density
    fun dpi(context: Context, v: Float) = dp(context, v).toInt()

    /** A rounded rectangle filled with [fill] and edged with a hairline in [stroke]. */
    fun card(context: Context, fill: Int = SURFACE, stroke: Int = HAIRLINE, radius: Float = 22f, strokeDp: Float = 1f) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(context, radius)
            setStroke(dpi(context, strokeDp).coerceAtLeast(1), stroke)
        }

    /** [base] with a soft gold ripple on touch. */
    fun pressable(context: Context, base: GradientDrawable, radius: Float = 22f) = RippleDrawable(
        ColorStateList.valueOf(0x33D4B06A),
        base,
        GradientDrawable().apply { setColor(0xFFFFFFFF.toInt()); cornerRadius = dp(context, radius) },
    )

    /** Small letter-spaced capitals used above each section. */
    fun eyebrow(context: Context, text: String, color: Int = GOLD) = TextView(context).apply {
        this.text = text.uppercase()
        setTextColor(color)
        typeface = MEDIUM
        textSize = 11f
        letterSpacing = 0.22f
    }

    fun text(context: Context, text: String, size: Float, color: Int = IVORY, face: Typeface = REGULAR) =
        TextView(context).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            typeface = face
            setLineSpacing(0f, 1.18f)
        }

    /** The gold call-to-action button. */
    fun goldButton(context: Context, label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 15f
        typeface = MEDIUM
        letterSpacing = 0.04f
        setTextColor(INK)
        val pad = dpi(context, 16f)
        setPadding(pad, pad, pad, pad)
        background = pressable(context, GradientDrawable().apply {
            colors = intArrayOf(0xFFE6C88A.toInt(), GOLD, GOLD_DEEP)
            orientation = GradientDrawable.Orientation.TL_BR
            cornerRadius = dp(context, 30f)
        }, 30f)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }
}

/** A slim toggle in the house style: a hairline track that fills with gold when switched on. */
@SuppressLint("ViewConstructor")
class LuxSwitch(context: Context, checked: Boolean, private val onChange: (Boolean) -> Unit) : View(context) {

    var isChecked = checked
        private set

    private var pos = if (checked) 1f else 0f
    private var anim: ValueAnimator? = null
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val w = Lux.dp(context, 46f)
    private val h = Lux.dp(context, 26f)

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { toggle() }
    }

    fun toggle() = set(!isChecked, animate = true, notify = true)

    fun set(on: Boolean, animate: Boolean, notify: Boolean) {
        if (on == isChecked) return
        isChecked = on
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        anim?.cancel()
        if (animate) {
            anim = ValueAnimator.ofFloat(pos, if (on) 1f else 0f).apply {
                duration = 220
                interpolator = DecelerateInterpolator()
                addUpdateListener { pos = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            pos = if (on) 1f else 0f
            invalidate()
        }
        if (notify) onChange(on)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(w.toInt(), h.toInt())

    override fun onDraw(canvas: Canvas) {
        val r = h / 2f
        rect.set(0f, 0f, w, h)
        // track: hairline when off, gold when on
        track.style = Paint.Style.FILL
        track.color = blend(Lux.SURFACE_HI, Lux.GOLD, pos)
        canvas.drawRoundRect(rect, r, r, track)
        track.style = Paint.Style.STROKE
        track.strokeWidth = Lux.dp(context, 1f)
        track.color = blend(Lux.HAIRLINE, Lux.GOLD, pos)
        rect.inset(track.strokeWidth / 2f, track.strokeWidth / 2f)
        canvas.drawRoundRect(rect, r, r, track)
        val inset = Lux.dp(context, 4f)
        val tr = r - inset
        val cx = r + (w - 2f * r) * pos
        thumb.color = blend(Lux.MUTED, Lux.INK, pos)
        canvas.drawCircle(cx, r, tr, thumb)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Switch"
        info.isCheckable = true
        info.isChecked = isChecked
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = ((a shr shift and 0xFF) + ((b shr shift and 0xFF) - (a shr shift and 0xFF)) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
