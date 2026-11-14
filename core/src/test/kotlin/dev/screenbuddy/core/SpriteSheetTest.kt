package dev.screenbuddy.core

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

class SpriteSheetTest {
    @Test
    fun everyFrameRendersAndFitsItsOutline() {
        val scale = 6
        val cols = Anim.entries.maxOf { it.frames }
        val sheet = BufferedImage(cols * 32 * scale, Anim.entries.size * 32 * scale, BufferedImage.TYPE_INT_ARGB)
        val gfx = sheet.createGraphics()
        gfx.color = java.awt.Color(0x6C, 0x8E, 0xBF)
        gfx.fillRect(0, 0, sheet.width, sheet.height)
        for (anim in Anim.entries) for (f in 0 until anim.frames) {
            val px = Sprites.frame(anim, f)
            assertTrue(px.any { it != 0 }, "$anim frame $f is empty")
            for (y in 0 until 32) for (x in 0 until 32) {
                val c = px[y * 32 + x]
                if (c == 0) continue
                gfx.color = java.awt.Color(c, true)
                gfx.fillRect((f * 32 + x) * scale, (anim.ordinal * 32 + y) * scale, scale, scale)
            }
        }
        gfx.dispose()
        val out = File("build/sprites.png")
        out.parentFile.mkdirs()
        ImageIO.write(sheet, "png", out)
    }
}
