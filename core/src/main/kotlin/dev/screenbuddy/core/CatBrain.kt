package dev.screenbuddy.core

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The cat's behaviour and physics, free of any Android types so it can be unit tested.
 *
 * Coordinates are screen pixels; ([x], [y]) is the top-left of the square sprite of side [size].
 * [floor] is the y coordinate of the surface the cat stands on (bottom of the screen, or the
 * top of the keyboard).
 */
class CatBrain(
    worldW: Float,
    floor: Float,
    size: Float,
    private val rng: Random = Random.Default,
) {
    enum class State { IDLE, WALK, ALERT, CHASE, AIR, LAND, PET, SLEEP, DRAG }

    var worldW = worldW
        private set
    var floor = floor
        private set
    var size = size
        private set

    var x = (worldW - size) / 2f
        private set
    var y = floor - size
        private set
    var facingRight = true
        private set
    var state = State.IDLE
        private set

    /** When false the cat still notices taps but does not run after them. */
    var chaseEnabled = true

    private var vx = 0f
    private var vy = 0f
    private var stateTime = 0f
    private var stateDur = 2f
    private var quietTime = 0f
    private var walkTo = 0f
    private var hasTarget = false
    private var targetX = 0f
    private var targetY = 0f

    private val standY get() = floor - size
    private val maxX get() = (worldW - size).coerceAtLeast(0f)
    private val gravity get() = 30f * size

    val anim: Anim
        get() = when (state) {
            State.IDLE -> Anim.IDLE
            State.WALK -> Anim.WALK
            State.ALERT -> Anim.ALERT
            State.CHASE -> Anim.RUN
            State.AIR -> if (vy < 0f) Anim.JUMP else Anim.FALL
            State.LAND -> Anim.LAND
            State.PET -> Anim.PET
            State.SLEEP -> Anim.SLEEP
            State.DRAG -> Anim.DRAG
        }

    val frame: Int get() = (stateTime * anim.fps).toInt() % anim.frames

    /** True while the cat is moving and wants to be ticked every display frame. */
    val isBusy: Boolean
        get() = state == State.WALK || state == State.CHASE || state == State.AIR ||
            state == State.DRAG || state == State.LAND || state == State.ALERT

    fun resize(worldW: Float, floor: Float, size: Float) {
        this.worldW = worldW
        this.floor = floor
        this.size = size
        x = x.coerceIn(0f, maxX)
        if (state != State.DRAG && state != State.AIR) {
            if (y > standY) y = standY else if (y < standY - 1f) enter(State.AIR)
        }
    }

    fun update(dtSeconds: Float) {
        val dt = dtSeconds.coerceIn(0f, 0.05f)
        stateTime += dt
        quietTime += dt
        when (state) {
            State.IDLE -> if (stateTime >= stateDur) {
                when {
                    quietTime > 40f && rng.nextFloat() < 0.5f -> enter(State.SLEEP, 40f + rng.nextFloat() * 80f)
                    rng.nextFloat() < 0.65f -> {
                        walkTo = rng.nextFloat() * maxX
                        enter(State.WALK)
                    }
                    else -> enter(State.IDLE)
                }
            }
            State.WALK -> if (moveToward(walkTo, 0.8f * size * dt)) enter(State.IDLE)
            State.ALERT -> if (stateTime >= stateDur) {
                if (hasTarget && chaseEnabled) enter(State.CHASE) else enter(State.IDLE)
            }
            State.CHASE -> chase(dt)
            State.AIR -> fly(dt)
            State.LAND -> if (stateTime >= 0.25f) enter(State.IDLE)
            State.PET -> if (stateTime >= 1.6f) enter(State.IDLE)
            State.SLEEP -> if (stateTime >= stateDur) enter(State.IDLE)
            State.DRAG -> Unit
        }
    }

    /** The user tapped something at this screen position. */
    fun onTap(tx: Float, ty: Float) {
        quietTime = 0f
        if (state == State.DRAG || state == State.AIR || state == State.SLEEP || state == State.PET) return
        if (state != State.CHASE && rng.nextFloat() > 0.8f) return // sometimes it can't be bothered
        hasTarget = true
        targetX = tx
        targetY = ty
        facingRight = tx >= x + size / 2f
        if (state != State.CHASE) enter(State.ALERT, 0.35f)
    }

    /** The user touched or scrolled somewhere, but we don't know where. */
    fun onNotice() {
        quietTime = 0f
        if (state == State.IDLE && rng.nextFloat() < 0.25f) {
            hasTarget = false
            enter(State.ALERT, 0.5f)
        }
    }

    fun onPet() {
        quietTime = 0f
        if (state == State.AIR) return
        enter(State.PET)
    }

    fun dragStart() {
        quietTime = 0f
        enter(State.DRAG)
    }

    fun dragTo(nx: Float, ny: Float) {
        if (state != State.DRAG) return
        x = nx.coerceIn(0f, maxX)
        y = ny.coerceIn(0f, standY)
    }

    /** Let go, optionally with a fling velocity in px/s. */
    fun dragEnd(velX: Float, velY: Float) {
        if (state != State.DRAG) return
        val cap = 40f * size
        vx = velX.coerceIn(-cap, cap)
        vy = velY.coerceIn(-cap, cap)
        enterAir()
    }

    private fun enter(next: State, duration: Float = 1.5f + rng.nextFloat() * 4f) {
        if (next == State.AIR) {
            vx = 0f
            vy = 0f
        }
        if (next != State.ALERT && next != State.CHASE) hasTarget = false
        state = next
        stateTime = 0f
        stateDur = duration
    }

    private fun enterAir() {
        state = State.AIR
        stateTime = 0f
    }

    /** Step horizontally toward [goal]; returns true on arrival. */
    private fun moveToward(goal: Float, step: Float): Boolean {
        val g = goal.coerceIn(0f, maxX)
        val dx = g - x
        if (abs(dx) <= step) {
            x = g
            return true
        }
        facingRight = dx > 0f
        x += sign(dx) * step
        return false
    }

    private fun chase(dt: Float) {
        val dx = targetX - (x + size / 2f)
        val height = (y + size * 0.3f) - targetY // how far above the cat's head the tap is
        val high = height > size * 0.5f
        val closeEnough = if (high) abs(dx) < 2.5f * size else abs(dx) < 0.4f * size
        val arrived = moveToward(targetX - size / 2f, 3f * size * dt)
        if (closeEnough || arrived || stateTime > 4f) {
            // leap so the top of the arc lands on the tap
            val h = if (high) height.coerceAtMost(5f * size) else 0.4f * size
            vy = -sqrt(2f * gravity * h)
            val t = -vy / gravity
            vx = (dx / t).coerceIn(-4f * size, 4f * size)
            if (abs(dx) > 1f) facingRight = dx > 0f
            hasTarget = false
            enterAir()
        }
    }

    private fun fly(dt: Float) {
        vy += gravity * dt
        x += vx * dt
        y += vy * dt
        if (abs(vx) > 1f) facingRight = vx > 0f
        if (x < 0f) {
            x = 0f
            vx = -vx * 0.5f
        } else if (x > maxX) {
            x = maxX
            vx = -vx * 0.5f
        }
        if (y < 0f) {
            y = 0f
            vy = abs(vy) * 0.3f
        }
        if (y >= standY && vy >= 0f) {
            y = standY
            if (vy > 12f * size) {
                vy = -vy * 0.3f
                vx *= 0.6f
            } else {
                vx = 0f
                vy = 0f
                enter(State.LAND)
            }
        }
    }
}
