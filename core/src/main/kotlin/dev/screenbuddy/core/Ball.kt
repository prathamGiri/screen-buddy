package dev.screenbuddy.core

import kotlin.math.abs

/** A bouncy ball of wool. ([x], [y]) is the top-left of its bounding square of side [d]. */
class Ball(var x: Float, var y: Float, val d: Float) {
    var vx = 0f
    var vy = 0f

    /** True while the user is holding the ball. */
    var held = false

    private var rolled = 0f

    val centerX get() = x + d / 2f
    val centerY get() = y + d / 2f

    /** Which of the [Sprites.BALL_FRAMES] rolling frames to show. */
    val frame: Int get() = Math.floorMod((rolled / (d * 0.25f)).toInt(), Sprites.BALL_FRAMES)

    val moving: Boolean get() = held || vx != 0f || vy != 0f

    fun kick(velX: Float, velY: Float) {
        vx = velX
        vy = velY
    }

    fun update(dt: Float, worldW: Float, floor: Float, gravity: Float) {
        if (held) return
        val maxX = (worldW - d).coerceAtLeast(0f)
        val restY = floor - d
        vy += gravity * dt
        x += vx * dt
        y += vy * dt
        rolled += vx * dt
        if (x < 0f) {
            x = 0f
            vx = -vx * 0.7f
        } else if (x > maxX) {
            x = maxX
            vx = -vx * 0.7f
        }
        if (y < 0f) {
            y = 0f
            vy = abs(vy) * 0.5f
        }
        if (y >= restY) {
            y = restY
            vy = if (vy > 3f * d) -vy * 0.55f else 0f
            // rolling friction
            vx -= vx * (1.8f * dt).coerceAtMost(1f)
            if (abs(vx) < 0.2f * d) vx = 0f
        }
    }
}
