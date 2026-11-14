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
    fun risesWithTheKeyboardAndFallsWhenItCloses() {
        val b = brain()
        b.resize(w, 1500f, size)
        assertEquals(1500f - size, b.y, 0.5f)
        b.resize(w, floor, size)
        assertEquals(CatBrain.State.AIR, b.state)
        b.run(3f)
        assertEquals(floor - size, b.y, 0.5f)
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
}
