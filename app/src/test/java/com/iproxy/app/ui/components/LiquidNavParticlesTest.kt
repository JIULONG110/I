package com.iproxy.app.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiquidNavParticlesTest {

    @Test
    fun `dark nav palette uses only blue cyan and deep navy colors without white`() {
        val checkedColors = listOf(
            DarkNavPalette.DockBgTop,
            DarkNavPalette.DockBgMid,
            DarkNavPalette.DockBgBottom,
            DarkNavPalette.DockSheenTop,
            DarkNavPalette.DockBorderTop,
            DarkNavPalette.DockBorderMid,
            DarkNavPalette.DockBorderBottom,
            DarkNavPalette.DockInnerBevelTop,
            DarkNavPalette.DockInnerBevelBottom,
            DarkNavPalette.PillGlowCenter,
            DarkNavPalette.PillGlowMid,
            DarkNavPalette.PillBodyTop,
            DarkNavPalette.PillBodyMid,
            DarkNavPalette.PillBodyBottom,
            DarkNavPalette.PillRimTop,
            DarkNavPalette.PillRimBottom,
            DarkNavPalette.IconSelected,
            DarkNavPalette.IconUnselected,
            DarkNavPalette.RippleBlue,
            DarkNavPalette.ParticleBlue,
            DarkNavPalette.ParticleCyan,
            DarkNavPalette.ParticleSky,
            DarkNavPalette.LinkBlue
        )

        checkedColors.forEach { color ->
            assertNotWhiteOrGray(color)
            assertTrue(
                color.blue >= color.red && color.blue >= color.green * 0.9f,
                "Color $color should stay in blue/cyan/navy spectrum"
            )
        }

        // 所有粒子色阶混合点也必须严格位于蓝/青蓝范围且非白色
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { mix ->
            val sampled = NavParticleField.colorForMix(mix)
            assertNotWhiteOrGray(sampled)
            assertTrue(sampled.blue > 0.85f && sampled.red < 0.35f)
        }
    }

    @Test
    fun `particle field trajectories stay bounded and smoothly curve over time`() {
        val count = NavParticleField.specs.size
        val x0 = FloatArray(count)
        val y0 = FloatArray(count)
        val a0 = FloatArray(count)
        val x1 = FloatArray(count)
        val y1 = FloatArray(count)
        val a1 = FloatArray(count)

        NavParticleField.sampleInto(0f, x0, y0, a0)
        NavParticleField.sampleInto(12.5f, x1, y1, a1)

        var movedCount = 0
        var curvedYCount = 0
        for (i in 0 until count) {
            assertTrue(y0[i] in 0.10f..0.90f, "Initial Y out of bounds: ${y0[i]}")
            assertTrue(y1[i] in 0.10f..0.90f, "Moved Y out of bounds: ${y1[i]}")
            assertTrue(a0[i] in 0f..1f && a1[i] in 0f..1f, "Alpha out of bounds")
            assertTrue(x0[i] in -0.09f..1.09f && x1[i] in -0.09f..1.09f)
            if (x0[i] != x1[i]) movedCount++
            if (y0[i] != y1[i]) curvedYCount++
        }

        assertEquals(count, movedCount)
        assertTrue(curvedYCount > count / 2)
    }

    private fun assertNotWhiteOrGray(color: Color) {
        val isWhiteLike = color.red > 0.85f && color.green > 0.85f && color.blue > 0.85f
        assertFalse(isWhiteLike, "Pure white / near-white color is forbidden: $color")
    }
}
