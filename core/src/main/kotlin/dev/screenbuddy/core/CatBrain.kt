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
    enum class State { IDLE, WALK, ALERT, CHASE, PLAY, AIR, LAND, PET, SLEEP, DRAG }

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

    /**
     * Calm mode, for when a demanding app is open: she stops wandering, chasing and playing, and
     * sits or naps so her window stays still. Petting, carrying and the keyboard still work.
     */
    var calm = false
        set(value) {
            field = value
            if (value && (state == State.WALK || state == State.CHASE || state == State.PLAY ||
                    (state == State.ALERT && y <= standY + 1f))
            ) {
                enter(State.IDLE)
            }
        }

    /** The ball of wool, while there is one to play with. */
    var ball: Ball? = null
        private set

    private var playLeft = 0f
    private var swatCooldown = 0f
    private var playMoving = false
    private var poseHold = 0f
    private var swatTime = 0f

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
            State.PLAY -> if (swatTime > 0f) Anim.SWAT else if (playMoving) Anim.RUN else Anim.WATCH
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
            state == State.DRAG || state == State.LAND || state == State.ALERT ||
            state == State.PLAY || ball?.moving == true

    fun resize(worldW: Float, floor: Float, size: Float) {
        this.worldW = worldW
        this.floor = floor
        this.size = size
        x = x.coerceIn(0f, maxX)
        if (state != State.DRAG && state != State.AIR && y < standY - 1f) enter(State.AIR)
    }

    /** A ball of wool drops in from the top of the screen. */
    fun giveBall() {
        if (ball != null) return
        val d = size * Sprites.BALL_SIZE / Sprites.SIZE
        ball = Ball((0.2f + rng.nextFloat() * 0.6f) * (worldW - d), 0f, d).also {
            it.kick((rng.nextFloat() - 0.5f) * 3f * size, 0f)
        }
        playLeft = 15f + rng.nextFloat() * 15f
        if (!calm && state != State.DRAG && state != State.AIR && state != State.ALERT) enter(State.ALERT, 0.5f)
    }

    fun takeBall() {
        ball = null
        if (state == State.PLAY) enter(State.IDLE)
    }

    fun ballGrab() {
        ball?.held = true
    }

    fun ballDragTo(nx: Float, ny: Float) {
        val b = ball ?: return
        if (!b.held) return
        b.x = nx.coerceIn(0f, (worldW - b.d).coerceAtLeast(0f))
        b.y = ny.coerceIn(0f, floor - b.d)
    }

    /** Let go of the ball, optionally throwing it with a velocity in px/s. */
    fun ballRelease(velX: Float, velY: Float) {
        val b = ball ?: return
        val cap = 40f * size
        b.held = false
        b.kick(velX.coerceIn(-cap, cap), velY.coerceIn(-cap, cap))
        playLeft = playLeft.coerceAtLeast(10f)
    }

    /** A tap on the ball nudges it. */
    fun ballPoke() {
        val b = ball ?: return
        b.kick((rng.nextFloat() - 0.5f) * 8f * size, -(6f + rng.nextFloat() * 4f) * size)
        playLeft = playLeft.coerceAtLeast(10f)
    }

    fun update(dtSeconds: Float) {
        val dt = dtSeconds.coerceIn(0f, 0.05f)
        stateTime += dt
        quietTime += dt
        ball?.update(dt, worldW, floor, gravity)
        // the floor rose above the cat (the keyboard opened): look up, then jump onto it
        if (y > standY + 1f && state != State.AIR && state != State.DRAG && state != State.ALERT) {
            hasTarget = false
            enter(State.ALERT, 0.6f)
        }
        when (state) {
            State.IDLE -> if (stateTime >= stateDur) {
                when {
                    calm -> if (rng.nextFloat() < 0.3f) enter(State.SLEEP, 40f + rng.nextFloat() * 80f) else enter(State.IDLE)
                    ball != null && playLeft > 0f -> startPlay()
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
                when {
                    y > standY + 1f -> {
                        val h = (y - standY) + 0.6f * size
                        vx = 0f
                        vy = -sqrt(2f * gravity * h)
                        enterAir()
                    }
                    calm -> enter(State.IDLE)
                    ball != null && playLeft > 0f -> startPlay()
                    hasTarget && chaseEnabled -> enter(State.CHASE)
                    else -> enter(State.IDLE)
                }
            }
            State.PLAY -> play(dt)
            State.CHASE -> chase(dt)
            State.AIR -> fly(dt)
            State.LAND -> if (stateTime >= 0.25f) {
                if (ball != null && playLeft > 0f && !calm) startPlay() else enter(State.IDLE)
            }
            State.PET -> if (stateTime >= 1.6f) enter(State.IDLE)
            State.SLEEP -> if (stateTime >= stateDur) enter(State.IDLE)
            State.DRAG -> Unit
        }
    }

    /** The user tapped something at this screen position. */
    fun onTap(tx: Float, ty: Float) {
        quietTime = 0f
        if (calm) return
        if (state == State.DRAG || state == State.AIR || state == State.SLEEP || state == State.PET) return
        if (state == State.PLAY || y > standY + 1f) return
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
        if (!calm && state == State.IDLE && rng.nextFloat() < 0.25f) {
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

    private fun startPlay() {
        enter(State.PLAY)
        playMoving = false
        poseHold = 0f
        swatTime = 0f
        play(0f) // pick the running or watching pose now, so no stray frame of the other shows
        if (state == State.PLAY && !playMoving) poseHold = 0.3f
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

    /** Run after the ball and bat it whenever it is within reach. */
    private fun play(dt: Float) {
        val b = ball
        playLeft -= dt
        swatCooldown -= dt
        if (b == null || playLeft <= 0f) {
            // bored for a few seconds; idling ends by going back to the ball
            playLeft = 10f + rng.nextFloat() * 10f
            enter(State.IDLE, 3f + rng.nextFloat() * 4f)
            return
        }
        poseHold -= dt
        swatTime -= dt
        val dx = b.centerX - (x + size / 2f)
        val adx = abs(dx)
        // Only set off after the ball once it is clearly out of reach, and keep each pose for a
        // moment, so she does not flicker between running and standing as it bounces overhead.
        if (playMoving) {
            if (adx < size * 0.2f) {
                playMoving = false
                poseHold = 0.35f
            }
        } else if (poseHold <= 0f && swatTime <= 0f && adx > size * (if (b.moving) 0.9f else 0.3f)) {
            playMoving = true
        }
        if (playMoving) {
            moveToward(b.centerX - size / 2f, 3f * size * dt)
        } else if (adx > size * 0.3f) {
            facingRight = dx > 0f
        }
        val withinReach = adx < size * 0.6f && b.centerY > y - size * 0.2f
        val settled = playMoving || poseHold <= 0.15f // no bat in the instant after stopping
        if (withinReach && settled && swatCooldown <= 0f && !b.held) {
            val dir = when {
                b.centerX < worldW * 0.2f -> 1f
                b.centerX > worldW * 0.8f -> -1f
                else -> if (rng.nextBoolean()) 1f else -1f
            }
            b.kick(dir * (1.5f + rng.nextFloat() * 2.5f) * size, -(8f + rng.nextFloat() * 6f) * size)
            facingRight = dir > 0f
            playMoving = false
            swatTime = 0.3f
            poseHold = 0.65f // stand and watch it fly before setting off again
            swatCooldown = 0.8f
            if (rng.nextFloat() < 0.2f) {
                // pounce after it
                vx = dir * 1.5f * size
                vy = -5f * size
                enterAir()
            }
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
                // mid-game she lands on her feet and keeps going, without the sit-down squash
                if (ball != null && playLeft > 0f && !calm) startPlay() else enter(State.LAND)
            }
        }
    }
}
