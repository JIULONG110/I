package com.iproxy.app.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.max

class LiquidNavParticlesTest {

    @Test
    fun `dark plasma glass palette uses saturated purple magenta electric blue cyan and darkened glass core`() {
        val plasmaAndAccentColors = listOf(
            DarkNavPalette.ParticleViolet,
            DarkNavPalette.ParticleMagenta,
            DarkNavPalette.ParticleElectricBlue,
            DarkNavPalette.ParticleCyan,
            DarkNavPalette.DockBorderTop,
            DarkNavPalette.DockBorderMid,
            DarkNavPalette.DockBorderBottom,
            DarkNavPalette.PillGlowCenter,
            DarkNavPalette.PillGlowMid,
            DarkNavPalette.PillBodyTop,
            DarkNavPalette.PillBodyMid,
            DarkNavPalette.PillBodyBottom,
            DarkNavPalette.GlassRimActiveTop,
            DarkNavPalette.GlassRimActiveBottom,
            DarkNavPalette.GlassRimIdleTop,
            DarkNavPalette.GlassRimIdleBottom,
            DarkNavPalette.GlassGlowRadialActive,
            DarkNavPalette.GlassGlowRadialIdle
        )

        plasmaAndAccentColors.forEach { color ->
            assertNotNeutralGray(color)
            val maxChannel = max(color.red, max(color.green, color.blue))
            assertTrue(
                color.blue >= 0.35f || color.red >= 0.45f,
                "Expected rich purple/magenta/electric-blue/cyan tone: $color"
            )
            assertTrue(maxChannel > 0.40f, "Accent/plasma color too dim: $color")
        }

        // 玻璃内部底色略压暗，保持粒子对比浓烈清晰
        listOf(DarkNavPalette.GlassIdleTop, DarkNavPalette.GlassIdleMid, DarkNavPalette.GlassIdleBottom).forEach { bg ->
            val lum = (bg.red + bg.green + bg.blue) / 3f
            assertTrue(lum < 0.14f, "Glass interior background should stay darkened for contrast: $bg")
        }

        // 粉白/青色高光发光粒子核心
        assertTrue(DarkNavPalette.ParticlePinkWhite.red > 0.90f && DarkNavPalette.ParticlePinkWhite.blue > 0.90f)
        assertTrue(DarkNavPalette.ParticleCyanWhite.green > 0.90f && DarkNavPalette.ParticleCyanWhite.blue > 0.95f)

        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { mix ->
            val sampled = RisingTubeParticleField.plasmaColorForMix(mix)
            assertNotNeutralGray(sampled)
        }
    }

    @Test
    fun `hero tube particle counts match 580 fine and 78 large reference specs and move rapidly upward`() {
        assertEquals(580, RisingTubeParticleField.fineSpecs.size)
        assertEquals(78, RisingTubeParticleField.largeSpecs.size)
        assertTrue(RisingTubeParticleField.fineSpecs.all { it.speedY >= 0.40f })
        assertTrue(RisingTubeParticleField.largeSpecs.all { it.speedY >= 0.32f })

        val fineCount = RisingTubeParticleField.fineSpecs.size
        val x0 = FloatArray(fineCount)
        val y0 = FloatArray(fineCount)
        val a0 = FloatArray(fineCount)
        val x1 = FloatArray(fineCount)
        val y1 = FloatArray(fineCount)
        val a1 = FloatArray(fineCount)

        NavParticleField.sampleInto(0f, x0, y0, a0, particleCount = fineCount, ySpeedScale = 1f, columnConcentrated = true)
        NavParticleField.sampleInto(0.25f, x1, y1, a1, particleCount = fineCount, ySpeedScale = 1f, columnConcentrated = true)

        var upwardMoved = 0
        for (i in 0 until fineCount) {
            assertTrue(x0[i] in 0.04f..0.96f && x1[i] in 0.04f..0.96f, "X out of inner tube bounds")
            assertTrue(y0[i] in -0.09f..1.09f && y1[i] in -0.09f..1.09f, "Y out of tube bounds")
            assertTrue(a0[i] in 0f..1f && a1[i] in 0f..1f, "Alpha out of bounds")
            if (y1[i] < y0[i]) upwardMoved++
        }
        // 在 0.25s 短时间窗内绝大多数未触顶回卷的粒子应严格向上位移（normY 减小）
        assertTrue(upwardMoved > fineCount * 0.70, "Expected rapid upward particle motion, got $upwardMoved/$fineCount")
    }

    @Test
    fun `particle counts scale proportionally by container area without stuffing small controls`() {
        val hero = RisingTubeParticleField.scaleParticleCounts(84f, 252f, isHeroTube = true)
        assertEquals(580, hero.fineCount)
        assertEquals(78, hero.largeCount)

        val checkbox = RisingTubeParticleField.scaleParticleCounts(22f, 22f)
        val switchPill = RisingTubeParticleField.scaleParticleCounts(52f, 28f)
        val modeButton = RisingTubeParticleField.scaleParticleCounts(112f, 44f)
        val dock = RisingTubeParticleField.scaleParticleCounts(384f, 74f, densityMultiplier = 0.62f)
        val dialog = RisingTubeParticleField.scaleParticleCounts(380f, 440f, densityMultiplier = 0.55f)

        assertTrue(checkbox.fineCount in 8..24 && checkbox.largeCount in 2..4)
        assertTrue(switchPill.fineCount in 28..64 && switchPill.largeCount in 4..10)
        assertTrue(modeButton.fineCount in 90..170 && modeButton.largeCount in 12..24)
        assertTrue(dock.fineCount < hero.fineCount && dock.fineCount > modeButton.fineCount)
        assertTrue(dialog.fineCount <= 480 && dialog.largeCount <= 66)
        assertTrue(checkbox.fineCount < switchPill.fineCount && switchPill.fineCount < modeButton.fineCount)
    }

    @Test
    fun `particle tail helpers never throw coerceIn range exceptions on compact or zero-height containers`() {
        val testHeightsPx = listOf(0f, 0.5f, 1f, 8f, 16f, 32f, 64f, 115f, 240f, 850f)
        val radiiPx = listOf(1f, 4.5f, 8.525f, 16f)

        for (h in testHeightsPx) {
            val fineTail = RisingTubeParticleField.safeFineTailPx(
                trailFactor = 0.025f,
                containerHeightPx = h,
                speedY = 0.9f
            )
            assertTrue(fineTail.isFinite() && fineTail >= 0f, "Invalid fine tail at h=$h: $fineTail")

            for (r in radiiPx) {
                val largeTail = RisingTubeParticleField.safeLargeTailPx(
                    trailFactor = 0.03f,
                    containerHeightPx = h,
                    speedY = 0.8f,
                    radiusPx = r
                )
                assertTrue(largeTail.isFinite() && largeTail >= 0f, "Invalid large tail at h=$h, r=$r: $largeTail")
            }
        }
    }

    @Test
    fun `app settings sanitize restores safe defaults for invalid ports and null Gson fields`() {
        val gson = com.google.gson.Gson()
        val legacyPartial = gson.fromJson(
            """{"routingMode":"GLOBAL_PROXY","socksPort":80,"vpnMtu":50}""",
            com.iproxy.app.data.model.AppSettings::class.java
        ).sanitized()

        assertEquals(com.iproxy.app.data.model.RoutingMode.GLOBAL_PROXY, legacyPartial.routingMode)
        assertEquals(10808, legacyPartial.socksPort)
        assertEquals(1500, legacyPartial.vpnMtu)
        assertEquals("1.1.1.1", legacyPartial.remoteDns)
        assertEquals(emptySet<String>(), legacyPartial.selectedAppPackages)
        assertEquals("", legacyPartial.customProxyRules)
    }

    private fun assertNotNeutralGray(color: Color) {
        val spread = max(color.red, max(color.green, color.blue)) -
            minOf(color.red, color.green, color.blue)
        val isNeutralGray = spread < 0.08f && color.red > 0.20f
        assertFalse(isNeutralGray, "Neutral gray color is forbidden: $color")
    }
}
