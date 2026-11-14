package dev.screenbuddy.core

/** Animations the cat can play. Frames are 32x32 ARGB pixel grids drawn in code. */
enum class Anim(val frames: Int, val fps: Float) {
    IDLE(8, 3f),
    WALK(4, 8f),
    RUN(4, 14f),
    ALERT(1, 1f),
    JUMP(1, 1f),
    FALL(1, 1f),
    LAND(1, 1f),
    PET(2, 3f),
    SLEEP(4, 1.5f),
    DRAG(2, 5f),
}

object Sprites {
    const val SIZE = 32
    const val BALL_SIZE = 12
    const val BALL_FRAMES = 4

    private const val OUTLINE = 0xFF2B2233.toInt()
    private const val FUR = 0xFFF2A03D.toInt()
    private const val STRIPE = 0xFFC46F28.toInt()
    private const val CREAM = 0xFFFDE8C4.toInt()
    private const val PINK = 0xFFF58AA0.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val HEART = 0xFFFF5C7A.toInt()
    private const val BANG = 0xFFFFD84A.toInt()
    private const val ZZZ = 0xFFBFE3FF.toInt()
    private const val WOOL = 0xFFE23B4E.toInt()
    private const val WOOL_DARK = 0xFFA81E3A.toInt()
    private const val WOOL_LIGHT = 0xFFFF98A2.toInt()

    private enum class Eyes { OPEN, CLOSED, HAPPY, WIDE }

    private class Grid(val n: Int = SIZE) {
        val px = IntArray(n * n)

        fun set(x: Int, y: Int, c: Int) {
            if (x in 0 until n && y in 0 until n) px[y * n + x] = c
        }

        fun rect(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
            for (y in y0..y1) for (x in x0..x1) set(x, y, c)
        }

        /** Surround every filled pixel with a dark outline. */
        fun outline() {
            val src = px.copyOf()
            for (y in 0 until n) for (x in 0 until n) {
                if (src[y * n + x] != 0) continue
                val near = (x > 0 && src[y * n + x - 1] != 0) ||
                    (x < n - 1 && src[y * n + x + 1] != 0) ||
                    (y > 0 && src[(y - 1) * n + x] != 0) ||
                    (y < n - 1 && src[(y + 1) * n + x] != 0)
                if (near) px[y * n + x] = OUTLINE
            }
        }
    }

    private val ballCache = arrayOfNulls<IntArray>(BALL_FRAMES)

    /** Pixels for the ball of wool, row-major ARGB, [BALL_SIZE] x [BALL_SIZE]. */
    fun ball(frame: Int): IntArray {
        val f = Math.floorMod(frame, BALL_FRAMES)
        ballCache[f]?.let { return it }
        val g = Grid(BALL_SIZE)
        for (y in 0 until BALL_SIZE) for (x in 0 until BALL_SIZE) {
            val dx = x - 5.5f
            val dy = y - 5.5f
            if (dx * dx + dy * dy > 21.5f) continue
            // diagonal strands that shift as the ball rolls
            g.set(x, y, if ((x + y + f) % 4 == 0) WOOL_DARK else WOOL)
        }
        g.set(3, 3, WOOL_LIGHT); g.set(4, 2, WOOL_LIGHT); g.set(3, 2, WOOL_LIGHT)
        g.outline()
        return g.px.also { ballCache[f] = it }
    }

    private val cache = HashMap<Int, IntArray>()

    /** Pixels for one frame, row-major ARGB, [SIZE] x [SIZE]. The cat faces right. */
    fun frame(anim: Anim, index: Int): IntArray {
        val i = ((index % anim.frames) + anim.frames) % anim.frames
        return cache.getOrPut(anim.ordinal * 64 + i) { render(anim, i).px }
    }

    private fun render(anim: Anim, i: Int): Grid {
        val g = Grid()
        when (anim) {
            Anim.IDLE -> {
                front(g, if (i == 5) Eyes.CLOSED else Eyes.OPEN, tail = if (i % 4 < 2) 0 else 1)
                g.outline()
            }
            Anim.ALERT -> {
                front(g, Eyes.WIDE, tail = 0)
                g.rect(27, 2, 28, 7, BANG)
                g.rect(27, 9, 28, 10, BANG)
                g.outline()
            }
            Anim.LAND -> {
                front(g, Eyes.CLOSED, tail = 1)
                g.outline()
            }
            Anim.PET -> {
                front(g, Eyes.HAPPY, tail = i)
                heart(g, 2, 3 - i)
                if (i == 1) heart(g, 25, 1)
                g.outline()
            }
            Anim.WALK, Anim.RUN -> {
                side(g, legs = i, tail = if (anim == Anim.RUN) 1 else i / 2, bob = if (anim == Anim.RUN && i % 2 == 1) -1 else 0)
                g.outline()
            }
            Anim.JUMP -> {
                side(g, legs = 4, tail = 1, bob = -2)
                g.outline()
            }
            Anim.FALL -> {
                side(g, legs = 5, tail = 0, bob = -2, eyes = Eyes.WIDE)
                g.outline()
            }
            Anim.SLEEP -> {
                loaf(g, breathe = i >= 2)
                g.outline()
                zzz(g, i)
            }
            Anim.DRAG -> {
                hang(g, swing = i)
                g.outline()
            }
        }
        return g
    }

    private fun eye(g: Grid, x: Int, y: Int, eyes: Eyes) {
        when (eyes) {
            Eyes.OPEN -> {
                g.rect(x, y, x + 1, y + 2, OUTLINE)
                g.set(x, y, WHITE)
            }
            Eyes.CLOSED -> g.rect(x, y + 1, x + 1, y + 1, OUTLINE)
            Eyes.HAPPY -> {
                g.set(x - 1, y + 2, OUTLINE)
                g.rect(x, y + 1, x + 1, y + 1, OUTLINE)
                g.set(x + 2, y + 2, OUTLINE)
            }
            Eyes.WIDE -> {
                g.rect(x - 1, y - 1, x + 2, y + 2, WHITE)
                g.rect(x, y, x + 1, y + 1, OUTLINE)
            }
        }
    }

    private fun heart(g: Grid, x: Int, y: Int) {
        g.set(x + 1, y, HEART); g.set(x + 3, y, HEART)
        g.rect(x, y + 1, x + 4, y + 1, HEART)
        g.rect(x + 1, y + 2, x + 3, y + 2, HEART)
        g.set(x + 2, y + 3, HEART)
    }

    /** Head with ears and face; (hx, hy) is the top-left of the 16x11 skull. */
    private fun head(g: Grid, hx: Int, hy: Int, eyes: Eyes) {
        // ears
        g.rect(hx, hy - 1, hx + 4, hy, FUR); g.rect(hx, hy - 2, hx + 3, hy - 2, FUR)
        g.rect(hx + 1, hy - 3, hx + 3, hy - 3, FUR); g.rect(hx + 1, hy - 4, hx + 2, hy - 4, FUR)
        g.rect(hx + 11, hy - 1, hx + 15, hy, FUR); g.rect(hx + 12, hy - 2, hx + 15, hy - 2, FUR)
        g.rect(hx + 12, hy - 3, hx + 14, hy - 3, FUR); g.rect(hx + 13, hy - 4, hx + 14, hy - 4, FUR)
        g.rect(hx + 1, hy - 1, hx + 2, hy - 1, PINK); g.set(hx + 2, hy - 2, PINK)
        g.rect(hx + 13, hy - 1, hx + 14, hy - 1, PINK); g.set(hx + 13, hy - 2, PINK)
        // skull
        g.rect(hx + 1, hy, hx + 14, hy + 10, FUR)
        g.rect(hx, hy + 1, hx + 15, hy + 9, FUR)
        // forehead stripes
        g.rect(hx + 7, hy, hx + 8, hy + 2, STRIPE)
        g.rect(hx + 4, hy, hx + 4, hy + 1, STRIPE)
        g.rect(hx + 11, hy, hx + 11, hy + 1, STRIPE)
        // muzzle
        g.rect(hx + 5, hy + 7, hx + 10, hy + 9, CREAM)
        g.rect(hx + 7, hy + 7, hx + 8, hy + 7, PINK)
        g.set(hx + 6, hy + 9, OUTLINE); g.set(hx + 9, hy + 9, OUTLINE)
        g.rect(hx + 7, hy + 8, hx + 8, hy + 8, OUTLINE)
        eye(g, hx + 3, hy + 4, eyes)
        eye(g, hx + 11, hy + 4, eyes)
    }

    /** Sitting, facing the viewer. */
    private fun front(g: Grid, eyes: Eyes, tail: Int) {
        // tail first so the body overlaps it
        if (tail == 0) {
            g.rect(22, 28, 25, 30, FUR)
            g.rect(25, 24, 26, 28, FUR)
            g.rect(25, 23, 26, 24, STRIPE)
        } else {
            g.rect(22, 29, 28, 30, FUR)
            g.rect(27, 28, 29, 29, STRIPE)
        }
        // body
        g.rect(10, 19, 21, 30, FUR)
        g.rect(9, 23, 22, 30, FUR)
        g.rect(13, 20, 18, 27, CREAM)
        // paws
        g.rect(11, 29, 13, 30, CREAM)
        g.rect(18, 29, 20, 30, CREAM)
        g.rect(14, 28, 17, 30, FUR)
        head(g, 8, 9, eyes)
    }

    /** Standing side-on, facing right. legs 0..3 walk cycle, 4 leaping, 5 falling. */
    private fun side(g: Grid, legs: Int, tail: Int, bob: Int, eyes: Eyes = Eyes.OPEN) {
        val b = bob
        // tail
        if (tail == 0) {
            g.rect(3, 13 + b, 4, 19 + b, FUR)
            g.rect(4, 11 + b, 5, 12 + b, STRIPE)
        } else {
            g.rect(1, 17 + b, 5, 18 + b, FUR)
            g.rect(0, 15 + b, 1, 16 + b, STRIPE)
        }
        // legs: back pair then front pair; "far" legs are darker
        fun leg(x: Int, dx: Int, lift: Int, far: Boolean) {
            val c = if (far) STRIPE else FUR
            g.rect(x + dx, 24 + b, x + dx + 1, 30 - lift + b * 0, c)
            g.rect(x + dx, 30 - lift, x + dx + 1, 30 - lift, if (far) STRIPE else CREAM)
        }
        when (legs) {
            0 -> { leg(9, -2, 0, true); leg(18, 2, 0, true); leg(7, 1, 0, false); leg(16, -1, 0, false) }
            1 -> { leg(9, 0, 2, true); leg(18, 0, 2, true); leg(7, 0, 0, false); leg(16, 0, 0, false) }
            2 -> { leg(9, 1, 0, true); leg(18, -1, 0, true); leg(7, -2, 0, false); leg(16, 2, 0, false) }
            3 -> { leg(9, 0, 0, true); leg(18, 0, 0, true); leg(7, 0, 2, false); leg(16, 0, 2, false) }
            4 -> {
                // stretched out mid-leap
                g.rect(2, 23 + b, 7, 24 + b, FUR); g.rect(1, 24 + b, 2, 25 + b, CREAM)
                g.rect(19, 23 + b, 24, 24 + b, FUR); g.rect(24, 24 + b, 25, 25 + b, CREAM)
            }
            else -> {
                // paws splayed downward while falling
                g.rect(6, 24 + b, 7, 28 + b, FUR); g.rect(5, 28 + b, 6, 29 + b, CREAM)
                g.rect(10, 24 + b, 11, 28 + b, STRIPE)
                g.rect(16, 24 + b, 17, 28 + b, STRIPE)
                g.rect(20, 24 + b, 21, 28 + b, FUR); g.rect(21, 28 + b, 22, 29 + b, CREAM)
            }
        }
        // body
        g.rect(6, 17 + b, 21, 24 + b, FUR)
        g.rect(7, 16 + b, 20, 16 + b, FUR)
        g.rect(9, 24 + b, 18, 24 + b, CREAM)
        for (sx in intArrayOf(9, 12, 15)) g.rect(sx, 16 + b, sx, 18 + b, STRIPE)
        head(g, 15, 8 + b, eyes)
    }

    /** Curled up asleep. */
    private fun loaf(g: Grid, breathe: Boolean) {
        val top = if (breathe) 19 else 20
        g.rect(9, top, 24, 30, FUR)
        g.rect(7, top + 2, 26, 30, FUR)
        for (sx in intArrayOf(18, 21, 24)) g.rect(sx, top, sx, top + 2, STRIPE)
        // tail wrapped along the front
        g.rect(16, 29, 28, 30, FUR)
        g.rect(16, 29, 18, 30, STRIPE)
        head(g, 3, 20, Eyes.CLOSED)
    }

    private fun zzz(g: Grid, i: Int) {
        fun z(x: Int, y: Int, n: Int) {
            g.rect(x, y, x + n, y, ZZZ)
            for (k in 1 until n) g.set(x + n - k, y + k, ZZZ)
            g.rect(x, y + n, x + n, y + n, ZZZ)
        }
        if (i >= 1) z(20, 12, 2)
        if (i >= 2) z(24, 6, 3)
        if (i >= 3) z(29, 1, 2)
    }

    /** Dangling while being carried. */
    private fun hang(g: Grid, swing: Int) {
        val s = if (swing == 0) -1 else 1
        // tail
        g.rect(20 + s, 24, 21 + s, 30, FUR)
        g.rect(20 + s, 29, 21 + s, 30, STRIPE)
        // legs
        g.rect(11 + s, 26, 13 + s, 30, FUR); g.rect(11 + s, 30, 13 + s, 30, CREAM)
        g.rect(17 + s, 26, 19 + s, 29, FUR); g.rect(17 + s, 29, 19 + s, 29, CREAM)
        // arms
        g.rect(7, 18 - s, 10, 19 - s, FUR); g.rect(7, 18 - s, 7, 19 - s, CREAM)
        g.rect(21, 18 + s, 24, 19 + s, FUR); g.rect(24, 18 + s, 24, 19 + s, CREAM)
        // body
        g.rect(11, 15, 20, 27, FUR)
        g.rect(13, 16, 18, 25, CREAM)
        head(g, 8, 5, Eyes.WIDE)
    }
}
