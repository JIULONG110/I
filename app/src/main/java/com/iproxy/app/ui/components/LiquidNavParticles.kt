package com.iproxy.app.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.Choreographer
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private fun computePlasmaColorForMix(hueMix: Float): Color {
    val m = hueMix.coerceIn(0f, 1f)
    return when {
        m < 0.28f -> lerp(DarkNavPalette.ParticleViolet, DarkNavPalette.ParticleMagenta, m / 0.28f)
        m < 0.54f -> lerp(DarkNavPalette.ParticleMagenta, DarkNavPalette.ParticleElectricBlue, (m - 0.28f) / 0.26f)
        m < 0.78f -> lerp(DarkNavPalette.ParticleElectricBlue, DarkNavPalette.ParticleCyan, (m - 0.54f) / 0.24f)
        else -> lerp(DarkNavPalette.ParticleCyan, DarkNavPalette.ParticlePinkWhite, ((m - 0.78f) / 0.22f) * 0.68f)
    }
}

private fun computeCoreColorForPreference(pref: Float): Color =
    if (pref < 0.64f) DarkNavPalette.ParticlePinkWhite else DarkNavPalette.ParticleCyanWhite

internal data class RisingParticleSpec(
    val baseX: Float,
    val baseY: Float,
    val speedY: Float,
    val swayAmp: Float,
    val swayFreq: Float,
    val driftPhase: Float,
    val radialBias: Float,
    val radiusDp: Float,
    val trailFactor: Float,
    val brightness: Float,
    val hueMix: Float,
    val twinkleFreq: Float,
    val twinklePhase: Float,
    val corePreference: Float,
    val isLarge: Boolean,
    val plasmaColor: Color = computePlasmaColorForMix(hueMix),
    val coreColor: Color = computeCoreColorForPreference(corePreference)
)

internal data class ScaledParticleCounts(
    val fineCount: Int,
    val largeCount: Int
)

/**
 * 深色高级玻璃与紫/洋红/电蓝/粉白/青色高饱和流光粒子调色板（严格压暗玻璃内底色以衬托浓缩高饱和粒子色彩）
 */
internal object DarkNavPalette {
    // 底部横向圆头胶囊导航壳（深暗靛紫玻璃底板 + 饱和电蓝/紫红高光边）
    val DockBgTop = Color(0xF7110D24)
    val DockBgMid = Color(0xFA090717)
    val DockBgBottom = Color(0xFC070A18)
    val DockSheenTop = Color(0x2BEBDFFF)
    val DockBorderTop = Color(0xC29F95FF)
    val DockBorderMid = Color(0x9C55D4FF)
    val DockBorderBottom = Color(0x889048FF)
    val DockInnerBevelTop = Color(0x63E4D4FF)
    val DockInnerBevelBottom = Color(0x3D4963D8)

    // 底部导航选中滑块胶囊（紫-洋红-电蓝实体发光）
    val PillGlowCenter = Color(0xA6A033FF)
    val PillGlowMid = Color(0x5CE833C4)
    val PillBodyTop = Color(0xDC6B2EFF)
    val PillBodyMid = Color(0xE03E22C8)
    val PillBodyBottom = Color(0xEB154BC6)
    val PillRimTop = Color(0xEBDCDEFF)
    val PillRimBottom = Color(0xA359DFFF)

    val IconSelected = Color(0xFFF6F0FF)
    val IconUnselected = Color(0xFFD5CEF2)
    val RippleBlue = Color(0xFFE95EFF)

    // 粒子光谱：紫 / 洋红 / 电蓝 + 粉白 / 青色发光粒子核
    val ParticleViolet = Color(0xFF832FFF)
    val ParticleMagenta = Color(0xFFEB2EC9)
    val ParticleElectricBlue = Color(0xFF1F74FF)
    val ParticleCyan = Color(0xFF3AEDFF)
    val ParticlePinkWhite = Color(0xFFFFDDF6)
    val ParticleCyanWhite = Color(0xFFBDF9FF)

    // 兼容语义别名（供通知与组件共享色调）
    val ParticleBlue = ParticleElectricBlue
    val ParticleSky = ParticleViolet
    val ParticleCoreIce = ParticleCyanWhite
    val LinkBlue = Color(0xFF7B6CFF)

    // 压暗内底色的 3D 实体玻璃容器（开启/选中态 vs 关闭/常态均保持饱满紫-电蓝-青玻璃光泽与高对比粒子）
    val GlassActiveTop = Color(0xE6341C68)
    val GlassActiveMid = Color(0xEB211454)
    val GlassActiveBottom = Color(0xF20D286B)
    val GlassIdleTop = Color(0xF5110E22)
    val GlassIdleMid = Color(0xFA080715)
    val GlassIdleBottom = Color(0xFC060A18)

    val GlassRimActiveTop = Color(0xEBE8D4FF)
    val GlassRimActiveBottom = Color(0xB84CD4FF)
    val GlassRimIdleTop = Color(0xA69F90EE)
    val GlassRimIdleBottom = Color(0x703D78DB)
    val GlassInnerBevelActive = Color(0x70EDD8FF)
    val GlassInnerBevelIdle = Color(0x478A7EE0)
    val GlassGlowRadialActive = Color(0x88BA3BFF)
    val GlassGlowRadialIdle = Color(0x4A4A2899)
    val GlassMeniscusTop = Color(0x30EFE4FF)
}

internal object RisingTubeParticleField {
    private const val TWO_PI = (PI * 2.0).toFloat()
    private const val TRACK_HEIGHT = 1.16f
    private const val TRACK_BOTTOM_START = 1.08f
    private const val DEFAULT_SNAPSHOT_SEC = 2.6f

    // 6 秒预览视频参考尺度：主竖直玻璃试管 580 颗细粒子 + 78 颗较大流光粒子
    const val HERO_FINE_COUNT = 580
    const val HERO_LARGE_COUNT = 78
    const val HERO_REFERENCE_WIDTH_DP = 84f
    const val HERO_REFERENCE_HEIGHT_DP = 252f
    const val HERO_REFERENCE_AREA_DP2 = HERO_REFERENCE_WIDTH_DP * HERO_REFERENCE_HEIGHT_DP // 21168f

    private fun deterministicHash(index: Int, salt: Int): Float {
        var x = (index + 1).toLong() * 747796405L + salt.toLong() * 2891336453L
        x = (x xor (x ushr 16)) * 2246822507L
        x = (x xor (x ushr 13)) * 3266489909L
        x = x xor (x ushr 16)
        return ((x and 0xFFFFFL).toFloat() / 0x100000L.toFloat()).coerceIn(0f, 0.9999f)
    }

    /**
     * 580 颗高速上冲细微流光粒子（含细晶粒与高速竖直拖尾微火花）
     */
    val fineSpecs: List<RisingParticleSpec> = List(HERO_FINE_COUNT) { idx ->
        val h1 = deterministicHash(idx, 11)
        val h2 = deterministicHash(idx, 23)
        val h3 = deterministicHash(idx, 37)
        val h4 = deterministicHash(idx, 53)
        val h5 = deterministicHash(idx, 71)
        val h6 = deterministicHash(idx, 89)
        val h7 = deterministicHash(idx, 103)
        val h8 = deterministicHash(idx, 131)
        val h9 = deterministicHash(idx, 157)
        val h10 = deterministicHash(idx, 181)

        // 中心汇聚加权正态近似 + 外围散布，形成高密束状向上喷涌的流光柱
        val tri = (h1 + h2 - 1f) * 0.5f
        val radialBias = tri.coerceIn(-0.48f, 0.48f)
        val baseX = ((idx * 0.61803398875f + 0.09f) % 1.0f)
        val baseY = ((idx * 0.38196601125f + h3 * 0.14f) % 1.0f)
        val isFastStreak = idx % 5 == 0

        RisingParticleSpec(
            baseX = baseX,
            baseY = baseY,
            speedY = if (isFastStreak) 0.68f + 0.62f * h4 else 0.40f + 0.56f * h4,
            swayAmp = 0.032f + 0.072f * h5,
            swayFreq = 0.95f + 0.85f * h6,
            driftPhase = TWO_PI * h7,
            radialBias = radialBias,
            radiusDp = if (isFastStreak) 0.52f + 0.48f * h8 else 0.42f + 0.72f * h8,
            trailFactor = if (isFastStreak) 0.011f + 0.011f * h9 else 0.003f + 0.004f * h9,
            brightness = 0.44f + 0.56f * h10,
            hueMix = deterministicHash(idx, 211),
            twinkleFreq = 0.65f + 0.85f * h6,
            twinklePhase = TWO_PI * h3,
            corePreference = deterministicHash(idx, 241),
            isLarge = false
        )
    }

    /**
     * 78 颗较大流光景深粒子（饱满紫/洋红/电蓝光团配高光粉白或青色亮核）
     */
    val largeSpecs: List<RisingParticleSpec> = List(HERO_LARGE_COUNT) { idx ->
        val seed = idx + 10_000
        val h1 = deterministicHash(seed, 17)
        val h2 = deterministicHash(seed, 31)
        val h3 = deterministicHash(seed, 47)
        val h4 = deterministicHash(seed, 67)
        val h5 = deterministicHash(seed, 97)
        val h6 = deterministicHash(seed, 113)
        val h7 = deterministicHash(seed, 149)
        val h8 = deterministicHash(seed, 173)
        val h9 = deterministicHash(seed, 199)

        val tri = (h1 + h2 - 1f) * 0.45f
        RisingParticleSpec(
            baseX = ((idx * 0.61803398875f + 0.21f) % 1.0f),
            baseY = ((idx * 0.38196601125f + h3 * 0.12f) % 1.0f),
            speedY = 0.32f + 0.50f * h4,
            swayAmp = 0.036f + 0.075f * h5,
            swayFreq = 0.80f + 0.70f * h6,
            driftPhase = TWO_PI * h7,
            radialBias = tri.coerceIn(-0.44f, 0.44f),
            radiusDp = 1.55f + 1.85f * h8,
            trailFactor = 0.008f + 0.009f * h9,
            brightness = 0.58f + 0.42f * deterministicHash(seed, 223),
            hueMix = deterministicHash(seed, 251),
            twinkleFreq = 0.52f + 0.65f * h6,
            twinklePhase = TWO_PI * h3,
            corePreference = deterministicHash(seed, 277),
            isLarge = true
        )
    }

    /**
     * 粒子光谱映射：紫 (0..0.28) -> 洋红 (0.28..0.54) -> 电蓝 (0.54..0.78) -> 青色 (0.78..1.0)
     */
    fun plasmaColorForMix(hueMix: Float): Color = computePlasmaColorForMix(hueMix)

    /**
     * 内核亮芯颜色：约 65% 粉白发光星核、约 35% 亮青发光星核
     */
    fun coreColorForPreference(pref: Float): Color = computeCoreColorForPreference(pref)

    /**
     * 根据真实控件 dp 面积等比例缩放细粒子与较大流光粒子数：
     * - 主竖直试管严格保留参考密度（580 细 + 78 较大流光）
     * - 小型开关/复选框/标签等按面积线性缩减，既饱满流动又不拥挤超载
     * - 超宽卡片或大弹窗在等比例基础上做流畅度上限，防止遮挡文字与过多 GPU 绘制
     */
    fun scaleParticleCounts(
        widthDp: Float,
        heightDp: Float,
        isHeroTube: Boolean = false,
        densityMultiplier: Float = 1f,
        reduceMotion: Boolean = false
    ): ScaledParticleCounts {
        if (isHeroTube) {
            return if (reduceMotion) {
                ScaledParticleCounts(fineCount = 128, largeCount = 18)
            } else {
                ScaledParticleCounts(fineCount = HERO_FINE_COUNT, largeCount = HERO_LARGE_COUNT)
            }
        }

        val safeArea = (widthDp.coerceAtLeast(12f) * heightDp.coerceAtLeast(8f))
        val areaRatio = safeArea / HERO_REFERENCE_AREA_DP2
        val effectiveRatio = (areaRatio * densityMultiplier.coerceIn(0.15f, 1.5f)).coerceAtMost(0.82f)
        var fine = (HERO_FINE_COUNT * effectiveRatio).roundToInt().coerceIn(8, HERO_FINE_COUNT)
        var large = (HERO_LARGE_COUNT * effectiveRatio).roundToInt().coerceIn(2, HERO_LARGE_COUNT)

        if (reduceMotion) {
            fine = (fine * 0.22f).roundToInt().coerceAtLeast(4)
            large = (large * 0.25f).roundToInt().coerceAtLeast(1)
        }
        return ScaledParticleCounts(fineCount = fine, largeCount = large)
    }

    internal inline fun sampleSpecValues(
        spec: RisingParticleSpec,
        elapsedSec: Float,
        timeOffsetSec: Float,
        ySpeedScale: Float,
        columnConcentrated: Boolean,
        spreadFraction: Float = 0.88f,
        onSample: (normX: Float, normY: Float, alpha: Float) -> Unit
    ) {
        val t = elapsedSec + timeOffsetSec
        val travel = (spec.baseY + t * spec.speedY * ySpeedScale)
        val wrapped = ((travel % TRACK_HEIGHT) + TRACK_HEIGHT) % TRACK_HEIGHT
        val normY = TRACK_BOTTOM_START - wrapped // 从 1.08 (底部) 迅速向上冲向 -0.08 (顶部)

        // 底部发射加速感与顶部圆头平滑隐退
        val edgeFade = smoothstep(1.06f, 0.90f, normY) * smoothstep(-0.06f, 0.10f, normY)
        val wave = spec.swayAmp * sin(TWO_PI * (normY * spec.swayFreq + t * 0.38f) + spec.driftPhase)
        val normX = if (columnConcentrated) {
            // 竖直试管形态：75% 汇聚于饱满高速流光柱，25% 贴近内玻璃壁流动
            val colX = 0.50f + spec.radialBias * 0.64f + wave
            val rimBiasX = 0.10f + 0.80f * spec.baseX + wave * 0.5f
            val blend = if (abs(spec.radialBias) > 0.36f) 0.45f else 0.92f
            (colX * blend + rimBiasX * (1f - blend)).coerceIn(0.06f, 0.94f)
        } else {
            val halfSpan = (spreadFraction * 0.5f).coerceIn(0.25f, 0.47f)
            val centered = 0.5f + (spec.baseX - 0.5f) * (halfSpan * 2f) + wave
            centered.coerceIn(0.04f, 0.96f)
        }

        val twinkle = 0.72f + 0.28f * sin(TWO_PI * spec.twinkleFreq * t + spec.twinklePhase)
        val alpha = (spec.brightness * edgeFade * twinkle).coerceIn(0f, 1f)
        onSample(normX, normY, alpha)
    }

    fun sampleSpecAt(
        spec: RisingParticleSpec,
        elapsedSec: Float,
        timeOffsetSec: Float,
        ySpeedScale: Float,
        columnConcentrated: Boolean,
        spreadFraction: Float = 0.88f
    ): Triple<Float, Float, Float> {
        var rx = 0f
        var ry = 0f
        var ra = 0f
        sampleSpecValues(
            spec = spec,
            elapsedSec = elapsedSec,
            timeOffsetSec = timeOffsetSec,
            ySpeedScale = ySpeedScale,
            columnConcentrated = columnConcentrated,
            spreadFraction = spreadFraction
        ) { normX, normY, alpha ->
            rx = normX
            ry = normY
            ra = alpha
        }
        return Triple(rx, ry, ra)
    }

    fun initialSnapshotTimeSec(): Float = DEFAULT_SNAPSHOT_SEC

    internal fun safeLargeTailPx(
        trailFactor: Float,
        containerHeightPx: Float,
        speedY: Float,
        radiusPx: Float,
        maxHeightFraction: Float = 0.075f
    ): Float {
        if (!containerHeightPx.isFinite() || containerHeightPx <= 0f) return 0f
        val maxTail = (containerHeightPx * maxHeightFraction).coerceAtLeast(0.5f)
        val safeRadius = if (radiusPx.isFinite() && radiusPx > 0f) radiusPx else 0f
        val minTail = (safeRadius * 1.1f).coerceAtMost(maxTail)
        val raw = trailFactor * containerHeightPx * speedY * 1.45f
        val finiteRaw = if (raw.isFinite()) raw else minTail
        return finiteRaw.coerceIn(minTail, maxTail)
    }

    internal fun safeFineTailPx(
        trailFactor: Float,
        containerHeightPx: Float,
        speedY: Float,
        maxHeightFraction: Float = 0.055f
    ): Float {
        if (!containerHeightPx.isFinite() || containerHeightPx <= 0f) return 0f
        val maxTail = (containerHeightPx * maxHeightFraction).coerceAtLeast(0.4f)
        val minTail = 1.8f.coerceAtMost(maxTail)
        val raw = trailFactor * containerHeightPx * speedY * 1.55f
        val finiteRaw = if (raw.isFinite()) raw else minTail
        return finiteRaw.coerceIn(minTail, maxTail)
    }

    internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge0 == edge1) return 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

/**
 * 兼容与通知/旧调用层对齐的轻量粒子字段视图（直接透传 RisingTubeParticleField 的快速上冲粒子规格）
 */
internal object NavParticleField {
    val specs: List<RisingParticleSpec>
        get() = RisingTubeParticleField.fineSpecs

    fun colorForMix(hueMix: Float): Color = RisingTubeParticleField.plasmaColorForMix(hueMix)

    fun initialSnapshotTimeSec(): Float = RisingTubeParticleField.initialSnapshotTimeSec()

    fun sampleInto(
        elapsedSec: Float,
        outNormX: FloatArray,
        outNormY: FloatArray,
        outAlpha: FloatArray,
        particleCount: Int = specs.size,
        ySpeedScale: Float = 1f,
        timeOffsetSec: Float = 0f,
        columnConcentrated: Boolean = false
    ) {
        val count = particleCount.coerceIn(0, specs.size)
        for (i in 0 until count) {
            RisingTubeParticleField.sampleSpecValues(
                spec = specs[i],
                elapsedSec = elapsedSec,
                timeOffsetSec = timeOffsetSec,
                ySpeedScale = ySpeedScale,
                columnConcentrated = columnConcentrated
            ) { nx, ny, a ->
                outNormX[i] = nx
                outNormY[i] = ny
                outAlpha[i] = a
            }
        }
    }
}

/**
 * 根级共享帧时钟 State：全屏所有玻璃控件仅在 Canvas 绘制阶段读取 state.value，
 * 避免每秒 60/120 次触发整个页面重组，保持零重组高帧率与可测试空闲判定。
 */
internal val LocalSharedParticleClockSec = staticCompositionLocalOf<State<Float>?> { null }

@Composable
internal fun ProvideParticleAnimationClock(content: @Composable () -> Unit) {
    val reduceMotion = rememberReduceMotionState()
    val elapsedState = rememberStandaloneParticleClockState(reduceMotion = reduceMotion)
    CompositionLocalProvider(LocalSharedParticleClockSec provides elapsedState) {
        content()
    }
}

internal fun isSystemReduceMotionEnabled(context: Context): Boolean {
    val scale = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
    }.getOrDefault(1f)
    return scale <= 0f || !ValueAnimator.areAnimatorsEnabled()
}

@Composable
internal fun rememberReduceMotionState(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var reduceMotion by remember(context) { mutableStateOf(isSystemReduceMotionEnabled(context)) }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reduceMotion = isSystemReduceMotionEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return reduceMotion
}

@Composable
private fun rememberStandaloneParticleClockState(reduceMotion: Boolean): State<Float> {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val inspection = LocalInspectionMode.current
    val elapsedState = remember { mutableFloatStateOf(RisingTubeParticleField.initialSnapshotTimeSec()) }
    DisposableEffect(reduceMotion, isResumed, inspection) {
        if (reduceMotion || !isResumed || inspection) {
            return@DisposableEffect onDispose {}
        }
        val choreographer = Choreographer.getInstance()
        var lastNanos = 0L
        var active = true
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!active) return
                val frameIntervalMs = if (lastNanos != 0L) (frameTimeNanos - lastNanos) / 1_000_000L else 0L
                if (lastNanos != 0L) {
                    val deltaSec = ((frameTimeNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    elapsedState.floatValue += deltaSec
                }
                lastNanos = frameTimeNanos
                if (frameIntervalMs > 28L) {
                    choreographer.postFrameCallbackDelayed(this, 24L)
                } else {
                    choreographer.postFrameCallback(this)
                }
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose {
            active = false
            choreographer.removeFrameCallback(callback)
        }
    }
    return elapsedState
}

@Composable
internal fun rememberParticleAnimationClockState(reduceMotion: Boolean): State<Float> {
    val shared = LocalSharedParticleClockSec.current
    if (shared != null) return shared
    return rememberStandaloneParticleClockState(reduceMotion = reduceMotion)
}

/**
 * 零分配绘制玻璃容器内部快速向上冲的紫/洋红/电蓝/粉白/青色粒子与流光尾迹，严格绘制在容器范围内。
 */
internal fun DrawScope.drawRisingPlasmaParticles(
    elapsedSec: Float,
    reduceMotion: Boolean,
    isHeroTube: Boolean = false,
    densityMultiplier: Float = 1f,
    referenceHeightDp: Float = RisingTubeParticleField.HERO_REFERENCE_HEIGHT_DP,
    timeOffsetSec: Float = 0f,
    intensity: Float = 1f,
    radiusScale: Float = 1f,
    columnConcentrated: Boolean? = null
) {
    val w = size.width
    val h = size.height
    if (w < 2f || h < 2f) return

    val wDp = w / density
    val hDp = h / density
    val counts = RisingTubeParticleField.scaleParticleCounts(
        widthDp = wDp,
        heightDp = hDp,
        isHeroTube = isHeroTube,
        densityMultiplier = densityMultiplier,
        reduceMotion = reduceMotion
    )

    val useColumn = columnConcentrated ?: (h > w * 1.35f)
    // 矮控件维持真实快速向上冲的体感线速度，高试管则匹配 ~6s 预览视频的全高穿越流速
    val ySpeedScale = if (isHeroTube) {
        1.0f
    } else {
        (referenceHeightDp / hDp.coerceAtLeast(18f)).coerceIn(0.85f, 2.8f)
    }
    val safeIntensity = intensity.coerceIn(0.18f, 1.35f)
    val fineSpecs = RisingTubeParticleField.fineSpecs
    val largeSpecs = RisingTubeParticleField.largeSpecs

    // 1. 底部上涌发射池内晕（在容器底部内腔形成紫色/洋红/电蓝聚焦光渊，激发粒子上冲质感）
    val plumeRadius = (if (useColumn) w * 0.78f else min(w * 0.45f, h * 1.45f)).coerceAtLeast(8f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF9F2CFF).copy(alpha = (0.34f * safeIntensity).coerceAtMost(0.55f)),
                Color(0xFF1D6DFF).copy(alpha = (0.20f * safeIntensity).coerceAtMost(0.32f)),
                Color.Transparent
            ),
            center = Offset(w * 0.5f, h * 0.90f),
            radius = plumeRadius
        ),
        radius = plumeRadius,
        center = Offset(w * 0.5f, h * 0.90f)
    )

    // 2. 较大流光景深粒子（78 颗或按容器面积等比例缩减，含流光尾迹 + 饱和外晕 + 粉白/青高光核）
    val largeLimit = counts.largeCount.coerceIn(0, largeSpecs.size)
    for (i in 0 until largeLimit) {
        val spec = largeSpecs[i]
        RisingTubeParticleField.sampleSpecValues(
            spec = spec,
            elapsedSec = elapsedSec,
            timeOffsetSec = timeOffsetSec,
            ySpeedScale = ySpeedScale,
            columnConcentrated = useColumn
        ) { nx, ny, rawAlpha ->
            if (rawAlpha <= 0.02f) return@sampleSpecValues

            val alpha = (rawAlpha * safeIntensity).coerceIn(0f, 1f)
            val cx = nx * w
            val cy = ny * h
            val pos = Offset(cx, cy)
            val rPx = spec.radiusDp.dp.toPx() * radiusScale
            val plasma = spec.plasmaColor
            val core = spec.coreColor

            // 柔和上冲星尾（安全钳制，避免矮控件高度下限大于上限触发 coerceIn IllegalArgumentException）
            val tailPx = RisingTubeParticleField.safeLargeTailPx(
                trailFactor = spec.trailFactor,
                containerHeightPx = h,
                speedY = spec.speedY,
                radiusPx = rPx
            )
            drawLine(
                color = plasma.copy(alpha = (alpha * 0.26f).coerceAtMost(0.36f)),
                start = Offset(cx, cy + tailPx),
                end = pos,
                strokeWidth = (rPx * 0.85f).coerceAtLeast(1.0f),
                cap = StrokeCap.Round
            )

            // 层次 1：饱和紫/洋红/电蓝大外光晕
            drawCircle(
                color = plasma.copy(alpha = (alpha * 0.22f).coerceAtMost(0.32f)),
                radius = rPx * 2.50f,
                center = pos
            )
            // 层次 2：高饱和粒子本体
            drawCircle(
                color = plasma.copy(alpha = (alpha * 0.82f).coerceAtMost(0.90f)),
                radius = rPx,
                center = pos
            )
            // 层次 3：粉白或青色亮核
            drawCircle(
                color = core.copy(alpha = (alpha * 0.92f).coerceAtMost(0.96f)),
                radius = (rPx * 0.45f).coerceAtLeast(0.85f),
                center = pos
            )
        }
    }

    // 3. 高速细微粒子（580 颗或按容器面积等比例缩减，含细腻流光火花与星点）
    val fineLimit = counts.fineCount.coerceIn(0, fineSpecs.size)
    for (i in 0 until fineLimit) {
        val spec = fineSpecs[i]
        RisingTubeParticleField.sampleSpecValues(
            spec = spec,
            elapsedSec = elapsedSec,
            timeOffsetSec = timeOffsetSec,
            ySpeedScale = ySpeedScale,
            columnConcentrated = useColumn
        ) { nx, ny, rawAlpha ->
            if (rawAlpha <= 0.03f) return@sampleSpecValues

            val alpha = (rawAlpha * safeIntensity).coerceIn(0f, 1f)
            val cx = nx * w
            val cy = ny * h
            val pos = Offset(cx, cy)
            val rPx = spec.radiusDp.dp.toPx() * radiusScale
            val plasma = spec.plasmaColor

            if (spec.trailFactor > 0.009f) {
                val tailLen = RisingTubeParticleField.safeFineTailPx(
                    trailFactor = spec.trailFactor,
                    containerHeightPx = h,
                    speedY = spec.speedY
                )
                drawLine(
                    color = plasma.copy(alpha = (alpha * 0.56f).coerceAtMost(0.70f)),
                    start = Offset(cx, cy + tailLen),
                    end = pos,
                    strokeWidth = (rPx * 1.35f).coerceAtLeast(0.9f),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = spec.coreColor.copy(alpha = (alpha * 0.90f).coerceAtMost(0.94f)),
                    radius = (rPx * 0.95f).coerceAtLeast(0.75f),
                    center = pos
                )
            } else {
                drawCircle(
                    color = plasma.copy(alpha = (alpha * 0.84f).coerceAtMost(0.90f)),
                    radius = rPx,
                    center = pos
                )
                if (rawAlpha > 0.54f) {
                    drawCircle(
                        color = spec.coreColor.copy(alpha = (alpha * 0.88f).coerceAtMost(0.94f)),
                        radius = (rPx * 0.56f).coerceAtLeast(0.65f),
                        center = pos
                    )
                }
            }
        }
    }
}

/**
 * 绘制压暗内底色的 3D 圆角实体玻璃容器：
 * - 严格保持深邃黑曜石-紫蓝玻璃底板，防止背景过灰/过亮冲淡粒子色彩
 * - 紫色 / 洋红 / 电蓝双层立体玻璃边框、顶部石英弯月高光及内缩玻璃管壁描边
 */
internal fun DrawScope.drawGlassCapsule3D(
    cornerRadiusDp: Dp,
    active: Boolean,
    elevated: Boolean = false,
    accentBorder: Color? = null,
    innerBevelInsetDp: Dp = 3.dp
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val r = cornerRadiusDp.toPx().coerceAtMost(min(w, h) * 0.5f)
    val cr = CornerRadius(r, r)

    // 1. 略压暗的深紫靛蓝实体玻璃主底色，确保内部高饱和粒子色彩浓厚、清晰
    val fillBrush = if (active) {
        Brush.linearGradient(
            0.0f to DarkNavPalette.GlassActiveTop,
            0.52f to DarkNavPalette.GlassActiveMid,
            1.0f to DarkNavPalette.GlassActiveBottom
        )
    } else {
        Brush.linearGradient(
            0.0f to if (elevated) Color(0xF7141129) else DarkNavPalette.GlassIdleTop,
            0.55f to DarkNavPalette.GlassIdleMid,
            1.0f to DarkNavPalette.GlassIdleBottom
        )
    }
    drawRoundRect(brush = fillBrush, cornerRadius = cr)

    // 2. 底部内腔紫/洋红/电蓝微光折射焦散（仅留在玻璃内部）
    val glowCenter = if (active) Offset(w * 0.5f, h * 0.56f) else Offset(w * 0.5f, h * 0.96f)
    val glowRadius = (if (active) size.maxDimension * 0.68f else min(w * 0.72f, h * 1.45f)).coerceAtLeast(4f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                if (active) DarkNavPalette.GlassGlowRadialActive else DarkNavPalette.GlassGlowRadialIdle,
                Color(0x281B64E8),
                Color.Transparent
            ),
            center = glowCenter,
            radius = glowRadius
        ),
        radius = glowRadius,
        center = glowCenter
    )

    // 3. 顶部实体玻璃弧形高光带
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                if (active) Color(0x3DF3E6FF) else DarkNavPalette.GlassMeniscusTop,
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.38f
        ),
        cornerRadius = cr
    )

    // 4. 内缩立体玻璃壁厚高光框
    val inset = innerBevelInsetDp.toPx().coerceAtMost(min(w, h) * 0.18f)
    if (inset >= 1f && w - inset * 2f > 4f && h - inset * 2f > 4f) {
        val innerR = (r - inset * 0.75f).coerceAtLeast(1f)
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    if (active) DarkNavPalette.GlassInnerBevelActive else DarkNavPalette.GlassInnerBevelIdle,
                    Color(0x243DC9FF)
                )
            ),
            topLeft = Offset(inset, inset),
            size = Size(w - inset * 2f, h - inset * 2f),
            cornerRadius = CornerRadius(innerR, innerR),
            style = Stroke(width = 0.9.dp.toPx())
        )
    }

    // 5. 外层 3D 立体紫/青/电蓝玻璃高光轮廓描边
    val borderBrush = when {
        accentBorder != null -> Brush.linearGradient(
            listOf(accentBorder.copy(alpha = 0.95f), Color(0xFF9F4DFF).copy(alpha = 0.72f), accentBorder.copy(alpha = 0.56f))
        )
        active -> Brush.linearGradient(
            listOf(DarkNavPalette.GlassRimActiveTop, Color(0xCCDA58FF), DarkNavPalette.GlassRimActiveBottom)
        )
        else -> Brush.linearGradient(
            listOf(DarkNavPalette.GlassRimIdleTop, Color(0x886A4CE8), DarkNavPalette.GlassRimIdleBottom)
        )
    }
    drawRoundRect(
        brush = borderBrush,
        cornerRadius = cr,
        style = Stroke(width = if (active || accentBorder != null) 1.35.dp.toPx() else 1.05.dp.toPx())
    )
}

/**
 * 可复用玻璃容器内部粒子流画布（全 App 卡片、按钮、开关、菜单、弹窗、加载条共用同一快速上冲引擎与面积自适应比例）。
 * 向外保留函数签名兼容同时彻底切换为 6 秒预览视频对应的快速上冲紫/洋红/电蓝+粉白/青色高密粒子。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun BlueConstellationParticlesCanvas(
    particleCount: Int = 0,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    referenceWidthDp: Float = 400f,
    linkMaxDistDp: Float = 46f,
    timeOffsetSec: Float = 0f,
    intensity: Float = 1f,
    radiusScale: Float = 1f,
    isHeroTube: Boolean = false,
    densityMultiplier: Float? = null,
    columnConcentrated: Boolean? = null
) {
    val clockState = rememberParticleAnimationClockState(reduceMotion = reduceMotion)
    val effectiveDensityMultiplier = densityMultiplier ?: when {
        isHeroTube -> 1.0f
        particleCount in 1..14 -> 0.95f
        particleCount in 15..24 -> 0.58f
        else -> 0.48f
    }

    Canvas(modifier = modifier) {
        val elapsedSec = if (reduceMotion) {
            RisingTubeParticleField.initialSnapshotTimeSec()
        } else {
            clockState.value
        }
        drawRisingPlasmaParticles(
            elapsedSec = elapsedSec,
            reduceMotion = reduceMotion,
            isHeroTube = isHeroTube,
            densityMultiplier = effectiveDensityMultiplier,
            timeOffsetSec = timeOffsetSec,
            intensity = intensity,
            radiusScale = radiusScale,
            columnConcentrated = columnConcentrated
        )
    }
}

/**
 * 完整圆头横向胶囊试管底部导航栏背景层：
 * 压暗的深紫靛玻璃底板 + 两端完全半圆封头玻璃折光边 + 内外双层管壁高光 + 等比例快速上冲流光粒子 + 选中高饱和胶囊滑块
 */
@Composable
internal fun DarkParticleDockBackdrop(
    animatedSelectedIndex: Float,
    tabCount: Int,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val clockState = rememberParticleAnimationClockState(reduceMotion = reduceMotion)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // 完整半圆封头的横向胶囊圆角
        val capsuleCorner = CornerRadius(h / 2f, h / 2f)

        // 1. 略压暗的深邃紫-靛玻璃底板，让内部流光粒子色彩浓、不混浊
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    DarkNavPalette.DockBgTop,
                    DarkNavPalette.DockBgMid,
                    DarkNavPalette.DockBgBottom
                )
            ),
            cornerRadius = capsuleCorner
        )

        // 玻璃内部管腔暗层与双端圆头弧形内折光
        val innerInset = 3.8.dp.toPx()
        val innerH = (h - innerInset * 2f).coerceAtLeast(1f)
        val innerCorner = CornerRadius(innerH / 2f, innerH / 2f)
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xA6130F2A), Color(0xB8060512)),
                startY = innerInset,
                endY = innerInset + innerH
            ),
            topLeft = Offset(innerInset, innerInset),
            size = Size((w - innerInset * 2f).coerceAtLeast(1f), innerH),
            cornerRadius = innerCorner
        )

        // 2. 选中 Tab 的紫/洋红/电蓝高饱和立体玻璃滑块（置于粒子之下、文本之下，保证选中项饱满透亮）
        if (tabCount > 0) {
            val innerPad = 5.dp.toPx()
            val rowWidth = (w - innerPad * 2f).coerceAtLeast(1f)
            val slotWidth = rowWidth / tabCount.toFloat()
            val pillHeight = (h - innerPad * 2f).coerceAtLeast(1f)
            val pillWidth = (slotWidth - 2.dp.toPx()).coerceAtLeast(pillHeight)
            val centerX = innerPad + (animatedSelectedIndex.coerceIn(0f, (tabCount - 1).toFloat()) + 0.5f) * slotWidth
            val centerY = h * 0.5f
            val pillLeft = centerX - pillWidth * 0.5f
            val pillCorner = CornerRadius(pillHeight / 2f, pillHeight / 2f)
            val glowRadius = (pillWidth * 0.86f).coerceAtLeast(1f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        DarkNavPalette.PillGlowCenter,
                        DarkNavPalette.PillGlowMid,
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(centerX, centerY)
            )

            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        DarkNavPalette.PillBodyTop,
                        DarkNavPalette.PillBodyMid,
                        DarkNavPalette.PillBodyBottom
                    ),
                    start = Offset(pillLeft, innerPad),
                    end = Offset(pillLeft + pillWidth * 0.9f, innerPad + pillHeight)
                ),
                topLeft = Offset(pillLeft, innerPad),
                size = Size(pillWidth, pillHeight),
                cornerRadius = pillCorner
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(DarkNavPalette.PillRimTop, Color(0xC2F16DFF), DarkNavPalette.PillRimBottom),
                    start = Offset(pillLeft, innerPad),
                    end = Offset(pillLeft + pillWidth, innerPad + pillHeight)
                ),
                topLeft = Offset(pillLeft, innerPad),
                size = Size(pillWidth, pillHeight),
                cornerRadius = pillCorner,
                style = Stroke(width = 1.15.dp.toPx())
            )
        }

        // 3. 等比例高速向上冲的紫/洋红/电蓝+粉白/青色粒子（严格留在横向胶囊内部，精细密度保持图标文字锐利可读）
        val elapsedSec = if (reduceMotion) {
            RisingTubeParticleField.initialSnapshotTimeSec()
        } else {
            clockState.value
        }
        drawRisingPlasmaParticles(
            elapsedSec = elapsedSec,
            reduceMotion = reduceMotion,
            isHeroTube = false,
            densityMultiplier = 0.36f,
            timeOffsetSec = 0.6f,
            intensity = 0.58f,
            radiusScale = 0.76f,
            columnConcentrated = false
        )

        // 4. 顶部弯月高光反射 + 内缩实体玻璃管壁 + 外圈高饱和紫蓝青立体描边
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(DarkNavPalette.DockSheenTop, Color.Transparent),
                startY = 0f,
                endY = h * 0.42f
            ),
            cornerRadius = capsuleCorner
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(DarkNavPalette.DockInnerBevelTop, DarkNavPalette.DockInnerBevelBottom)
            ),
            topLeft = Offset(innerInset, innerInset),
            size = Size((w - innerInset * 2f).coerceAtLeast(1f), innerH),
            cornerRadius = innerCorner,
            style = Stroke(width = 0.95.dp.toPx())
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    DarkNavPalette.DockBorderTop,
                    DarkNavPalette.DockBorderMid,
                    DarkNavPalette.DockBorderBottom
                )
            ),
            cornerRadius = capsuleCorner,
            style = Stroke(width = 1.25.dp.toPx())
        )
    }
}
