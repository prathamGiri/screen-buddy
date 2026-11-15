package dev.screenbuddy.core

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatBrainTest {
    private val w = 1080f
    private val floor = 2300f
    private val size = 192f

    private fun brain(seed: Int = 1) = CatBrain(w, floor, size, Random(seed))

    private fun CatBrain.run(seconds: Float, each: (CatBrain) -> Unit = {}) {
        var t = 0f
        while (t < seconds) {
            update(1f / 60f)
            each(this)
            t += 1f / 60f
        }
    }

    private fun assertInBounds(b: CatBrain) {
        assertTrue(b.x in 0f..(w - size), "x out of bounds: ${b.x}")
        assertTrue(b.y in 0f..(floor - size), "y out of bounds: ${b.y}")
    }

    @Test
    fun staysOnScreenWhileWandering() {
        for (seed in 1..5) {
            val b = brain(seed)
            b.run(300f) { assertInBounds(it) }
        }
    }

    @Test
    fun eventuallySleepsWhenLeftAlone() {
        val b = brain()
        var slept = false
        b.run(600f) { if (it.state == CatBrain.State.SLEEP) slept = true }
        assertTrue(slept)
    }

    @Test
    fun leapsAtAHighTapAndComesBackDown() {
        // the cat ignores about one tap in five, so try a few seeds and require most to chase
        var chased = 0
        for (seed in 1..10) {
            val b = brain(seed)
            val tapX = 900f
            val tapY = floor - size * 4f
            b.onTap(tapX, tapY)
            var closest = Float.MAX_VALUE
            b.run(6f) {
                assertInBounds(it)
                val d = abs(it.x + size / 2f - tapX) + abs(it.y + size * 0.3f - tapY)
                if (d < closest) closest = d
            }
            if (closest < size) chased++
            assertEquals(floor - size, b.y, 0.5f)
        }
        assertTrue(chased >= 6, "only $chased of 10 cats reached the tap")
    }

    @Test
    fun flingBouncesAndLands() {
        val b = brain()
        b.dragStart()
        b.dragTo(500f, 400f)
        assertEquals(CatBrain.State.DRAG, b.state)
        b.dragEnd(9000f, -3000f)
        b.run(8f) { assertInBounds(it) }
        assertEquals(floor - size, b.y, 0.5f)
        assertTrue(b.state != CatBrain.State.AIR)
    }

    @Test
    fun pettingWakesTheCat() {
        val b = brain()
        b.run(600f) { if (it.state == CatBrain.State.SLEEP) return@run }
        b.onPet()
        assertEquals(CatBrain.State.PET, b.state)
        b.run(2f)
        assertEquals(CatBrain.State.IDLE, b.state)
    }

    @Test
    fun jumpsOntoTheKeyboardAfterItOpensAndFallsWhenItCloses() {
        val b = brain()
        b.resize(w, 1500f, size)
        // the keyboard covers her first; she has not moved yet
        assertEquals(floor - size, b.y, 0.5f)
        b.run(0.3f)
        assertEquals(floor - size, b.y, 0.5f)
        var peak = Float.MAX_VALUE
        b.run(3f) { if (it.y < peak) peak = it.y }
        assertTrue(peak < 1500f - size, "she should jump above the keyboard, peak=$peak")
        assertEquals(1500f - size, b.y, 0.5f)
        b.resize(w, floor, size)
        assertEquals(CatBrain.State.AIR, b.state)
        b.run(3f)
        assertEquals(floor - size, b.y, 0.5f)
    }

    @Test
    fun wakesUpToClimbTheKeyboard() {
        val b = brain()
        b.run(600f) { if (it.state == CatBrain.State.SLEEP) return@run }
        b.resize(w, 1500f, size)
        b.run(3f)
        assertEquals(1500f - size, b.y, 0.5f)
    }

    @Test
    fun playsWithTheBallAndKeepsItOnScreen() {
        for (seed in 1..5) {
            val b = brain(seed)
            b.giveBall()
            var played = false
            var batted = false
            var t = 0f
            var lastAnim = b.anim
            var sinceChange = 0f
            var quickChanges = 0
            b.run(40f) {
                t += 1f / 60f
                val ball = it.ball!!
                assertInBounds(it)
                assertTrue(ball.x in 0f..(w - ball.d) && ball.y in 0f..(floor - ball.d), "ball out of bounds")
                if (it.state == CatBrain.State.PLAY) played = true
                sinceChange += 1f / 60f
                if (it.anim != lastAnim) {
                    if (sinceChange < 0.15f) quickChanges++
                    lastAnim = it.anim
                    sinceChange = 0f
                }
                // after the initial drop has settled, the ball only leaves the floor if she bats it
                if (t > 5f && ball.y < floor - ball.d - size) batted = true
            }
            assertTrue(played, "seed $seed never played")
            assertTrue(batted, "seed $seed never batted the ball")
            assertTrue(quickChanges <= 2, "seed $seed flickered between poses $quickChanges times in 40s")
            b.takeBall()
            b.run(3f)
            assertTrue(b.state != CatBrain.State.PLAY)
        }
    }

    @Test
    fun thrownBallComesToRest() {
        val b = brain()
        b.chaseEnabled = false
        b.giveBall()
        b.ballGrab()
        b.ballDragTo(300f, 300f)
        b.ballRelease(5000f, -2000f)
        b.takeBall() // physics only: check a fresh ball settles without the cat
        val ball = Ball(300f, 300f, 72f).also { it.kick(5000f, -2000f) }
        repeat(60 * 30) { ball.update(1f / 60f, w, floor, 30f * size) }
        assertTrue(!ball.moving, "ball still moving: ${ball.vx}, ${ball.vy}")
        assertEquals(floor - ball.d, ball.y, 0.5f)
    }

    @Test
    fun doesNotChaseWhenChasingIsOff() {
        for (seed in 1..10) {
            val b = brain(seed)
            b.chaseEnabled = false
            b.onTap(900f, 500f)
            b.run(1.5f) { assertTrue(it.state != CatBrain.State.CHASE && it.state != CatBrain.State.AIR) }
        }
    }

    @Test
    fun staysStillWhenCalm() {
        for (seed in 1..5) {
            val b = brain(seed)
            b.run(1f)
            b.giveBall()
            b.calm = true
            b.run(2f) // finish any jump or landing already under way
            val x0 = b.x
            b.onTap(100f, 500f)
            b.onNotice()
            b.run(120f) {
                assertEquals(x0, it.x, 0.5f, "seed $seed moved while calm")
                val still = it.state == CatBrain.State.IDLE || it.state == CatBrain.State.SLEEP ||
                    it.state == CatBrain.State.YAWN || it.state == CatBrain.State.STRETCH ||
                    it.state == CatBrain.State.GROOM || it.state == CatBrain.State.SCRATCH
                assertTrue(still, "seed $seed was ${it.state} while calm")
            }
            // she still lets herself be petted, and gets lively again afterwards
            b.onPet()
            assertEquals(CatBrain.State.PET, b.state)
            b.calm = false
            var moved = false
            b.run(90f) { if (abs(it.x - x0) > 1f) moved = true }
            assertTrue(moved, "seed $seed never moved after calm mode ended")
        }
    }

    @Test
    fun stillClimbsTheKeyboardWhenCalm() {
        val b = brain()
        b.calm = true
        b.resize(w, 1500f, size)
        b.run(3f)
        assertEquals(1500f - size, b.y, 0.5f)
    }

    /** Step the brain in coarse steps; enough for timers measured in minutes or hours. */
    private fun CatBrain.fastForward(seconds: Float, each: (CatBrain) -> Unit = {}) {
        var t = 0f
        while (t < seconds) {
            update(0.05f)
            each(this)
            t += 0.05f
        }
    }

    @Test
    fun aBallRollsInEveryHourOrTwoAndRollsAwayAfterAMinuteOrTwo() {
        for (seed in 1..5) {
            val b = brain(seed)
            var t = 0f
            while (b.ball == null && t < 7300f) {
                b.update(0.05f)
                t += 0.05f
            }
            assertTrue(t in 3600f..7205f, "seed $seed: ball arrived after ${t}s")
            var played = false
            var visit = 0f
            var sawLeave = false
            while (b.ball != null && visit < 200f) {
                b.update(1f / 60f)
                visit += 1f / 60f
                if (b.state == CatBrain.State.PLAY) played = true
                if (b.ball?.leaving == true) sawLeave = true
                assertInBounds(b)
            }
            assertTrue(played, "seed $seed never played with the ball")
            assertTrue(sawLeave, "seed $seed: the ball never rolled away")
            assertTrue(b.ball == null && visit < 150f, "seed $seed: ball stayed ${visit}s")
            assertTrue(b.ballTimer >= 3500f, "seed $seed: next ball due too soon (${b.ballTimer}s)")
        }
    }

    @Test
    fun noBallWhenBallPlayIsOff() {
        val b = brain()
        b.enabled = Behavior.entries.toSet() - Behavior.BALL
        b.fastForward(4f * 3600f) { assertTrue(it.ball == null) }
    }

    @Test
    fun turningBallPlayOffSendsTheBallAway() {
        val b = brain()
        b.giveBall()
        b.run(5f)
        b.enabled = Behavior.entries.toSet() - Behavior.BALL
        b.run(20f)
        assertTrue(b.ball == null)
        assertTrue(b.state != CatBrain.State.PLAY)
    }

    /** Every state change as (time, from, to) over [seconds] of simulated time. */
    private fun CatBrain.transitions(seconds: Float, each: (Float, CatBrain) -> Unit = { _, _ -> }): List<Triple<Float, CatBrain.State, CatBrain.State>> {
        val out = ArrayList<Triple<Float, CatBrain.State, CatBrain.State>>()
        var t = 0f
        var last = state
        while (t < seconds) {
            update(1f / 30f)
            t += 1f / 30f
            if (state != last) {
                out.add(Triple(t, last, state))
                last = state
            }
            each(t, this)
        }
        return out
    }

    @Test
    fun yawnsBeforeEveryNapAndStretchesOnlyAfterWakingByHerself() {
        for (seed in 1..5) {
            val b = brain(seed)
            val log = b.transitions(7200f) { _, it -> assertInBounds(it) }
            val naps = log.filter { it.third == CatBrain.State.SLEEP }
            val stretches = log.filter { it.third == CatBrain.State.STRETCH }
            assertTrue(naps.size >= 2, "seed $seed napped only ${naps.size} times in two hours")
            assertTrue(stretches.isNotEmpty(), "seed $seed never stretched")
            for (n in naps) assertEquals(CatBrain.State.YAWN, n.second, "seed $seed slept without yawning first")
            for (st in stretches) assertEquals(CatBrain.State.SLEEP, st.second, "seed $seed stretched without a nap")
        }
    }

    @Test
    fun doesNotStretchWhenWokenByATouch() {
        for (seed in 1..5) {
            val b = brain(seed)
            var t = 0f
            while (b.state != CatBrain.State.SLEEP && t < 3600f) {
                b.update(1f / 30f)
                t += 1f / 30f
            }
            assertEquals(CatBrain.State.SLEEP, b.state)
            b.onPet()
            val log = b.transitions(60f)
            assertTrue(log.none { it.third == CatBrain.State.STRETCH }, "seed $seed stretched after being woken")
        }
    }

    @Test
    fun groomingAndScratchingAreOccasional() {
        for (seed in 1..5) {
            val b = brain(seed)
            val log = b.transitions(7200f)
            val specials = log.filter {
                // a groom straight after a stretch is part of waking up, not a separate event
                (it.third == CatBrain.State.GROOM && it.second != CatBrain.State.STRETCH) ||
                    it.third == CatBrain.State.SCRATCH
            }
            val grooms = log.count { it.third == CatBrain.State.GROOM }
            val scratches = log.count { it.third == CatBrain.State.SCRATCH }
            assertTrue(grooms in 1..20, "seed $seed groomed $grooms times in two hours")
            assertTrue(scratches in 1..14, "seed $seed scratched $scratches times in two hours")
            for (i in 1 until specials.size) {
                val gap = specials[i].first - specials[i - 1].first
                assertTrue(gap >= 115f, "seed $seed: only ${gap}s between ${specials[i - 1].third} and ${specials[i].third}")
            }
        }
    }

    @Test
    fun switchedOffBehavioursNeverHappen() {
        for (seed in 1..5) {
            val b = brain(seed)
            b.enabled = setOf(Behavior.GROOM, Behavior.YAWN)
            val x0 = b.x
            b.run(2f) // finish the opening state
            b.run(600f) {
                val s = it.state
                assertTrue(
                    s == CatBrain.State.IDLE || s == CatBrain.State.GROOM || s == CatBrain.State.YAWN,
                    "seed $seed was $s",
                )
                assertEquals(x0, it.x, 0.5f)
            }
        }
    }

    @Test
    fun newAnimationsHoldTheirLastFrameAndLoopWhereTheyShould() {
        val b = brain()
        b.enabled = setOf(Behavior.STRETCH, Behavior.NAP)
        var maxFrame = 0
        b.run(1800f) {
            if (it.anim == Anim.STRETCH) {
                assertTrue(it.frame < Anim.STRETCH.frames)
                maxFrame = maxOf(maxFrame, it.frame)
            }
        }
        assertEquals(Anim.STRETCH.frames - 1, maxFrame)
    }
}
