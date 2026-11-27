package com.iproxy.app.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal data class NavParticleSpec(
    val baseX: Float,
    val baseY: Float,
    val speedX: Float,
    val arcAmp: Float,
    val arcFreq: Float,
    val verticalFreq: Float,
    val phase: Float,
    val radiusDp: Float,
    val brightness: Float,
    val hueMix: Float,
    val twinkleFreq: Float,
    val twinklePhase: Float,
    val isBokeh: Boolean
)

internal object DarkNavPalette {
    val DockBgTop = Color(0xF5142334)
    val DockBgMid = Color(0xF70E1A29)
    val DockBgBottom = Color(0xFA08111E)
    val DockSheenTop = Color(0x1AD3F0FF)
    val DockBorderTop = Color(0x8C5D92C5)
    val DockBorderMid = Color(0x70588DC0)
    val DockBorderBottom = Color(0x553B6B9B)
    val DockInnerBevelTop = Color(0x3B588DC0)
    val DockInnerBevelBottom = Color(0x241B4C84)

    val PillGlowCenter = Color(0x731F8EFF)
    val PillGlowMid = Color(0x3815549E)
    val PillBodyTop = Color(0x8F1779E6)
    val PillBodyMid = Color(0x9C0F5DBE)
    val PillBodyBottom = Color(0xA8074097)
    val PillRimTop = Color(0xAD79D1FF)
    val PillRimBottom = Color(0x664095E6)

    val IconSelected = Color(0xFFD6EEFF)
    val IconUnselected = Color(0xFFADBED1)
    val RippleBlue = Color(0xFF53CAFF)

    val ParticleBlue = Color(0xFF428CFF)
    val ParticleSky = Color(0xFF52BEFF)
    val ParticleCyan = Color(0xFF54E0FF)
    val LinkBlue = Color(0xFF57B3FF)
}

internal object NavParticleField {
    private const val TWO_PI = (PI * 2.0).toFloat()
    private const val TRACK_WIDTH = 1.16f
    private const val TRACK_MARGIN = 0.08f
    private const val DEFAULT_SNAPSHOT_SEC = 4.2f
    private const val MAX_LINKS_PER_NODE = 10
    private const val SPEC_COUNT = 104

    // 104 颗确定性层次粒子：对标原型 #navParticles（104）/ #coreParticles（48）/ .mode-particles（18），
    // 以 ~0.085..0.205/s (对应约 34..82 dp/s) 持续流动并构成青蓝星链
    val specs: List<NavParticleSpec> = List(SPEC_COUNT) { idx ->
        fun hash(salt: Int): Float {
            var x = (idx + 1).toLong() * 747796405L + salt.toLong() * 2891336453L
            x = (x xor (x ushr 16)) * 2246822507L
            x = (x xor (x ushr 13)) * 3266489909L
            x = x xor (x ushr 16)
            return ((x and 0xFFFFFL).toFloat() / 0x100000L.toFloat()).coerceIn(0f, 0.9999f)
        }

        val isBokeh = idx % 8 == 0
        // 黄金分割准随机横坐标：确保任意前缀子集（18 颗分流按钮 / 48 颗反应舱 / 104 颗底栏）在 0..1 区间均匀铺满
        val u0 = ((idx * 0.61803398875f + 0.137f) % 1.0f)
        val jitterX = (hash(1) - 0.5f) * 0.04f
        val baseX = (u0 + jitterX).let { ((it % 1f) + 1f) % 1f }
        val baseY = 0.14f + 0.72f * ((idx * 0.38196601125f + 0.271f) % 1.0f)
        val speedX = if (isBokeh) {
            0.082f + 0.058f * hash(3)
        } else {
            0.095f + 0.110f * hash(3)
        }
        val radiusDp = if (isBokeh) {
            1.65f + 0.75f * hash(4)
        } else {
            0.60f + 1.50f * hash(4)
        }
        val brightness = if (isBokeh) {
            0.24f + 0.14f * hash(5)
        } else {
            0.27f + 0.65f * hash(5)
        }
        NavParticleSpec(
            baseX = baseX,
            baseY = baseY,
            speedX = speedX,
            arcAmp = 0.04f + 0.10f * hash(6),
            arcFreq = 0.85f + 0.55f * hash(7),
            verticalFreq = 0.10f + 0.14f * hash(8),
            phase = TWO_PI * hash(9),
            radiusDp = radiusDp,
            brightness = brightness,
            hueMix = hash(10),
            twinkleFreq = 0.36f + 0.18f * hash(11),
            twinklePhase = TWO_PI * hash(12),
            isBokeh = isBokeh
        )
    }

    fun colorForMix(hueMix: Float): Color {
        val clamped = hueMix.coerceIn(0f, 1f)
        return if (clamped < 0.5f) {
            lerp(DarkNavPalette.ParticleBlue, DarkNavPalette.ParticleSky, clamped * 2f)
        } else {
            lerp(DarkNavPalette.ParticleSky, DarkNavPalette.ParticleCyan, (clamped - 0.5f) * 2f)
        }
    }

    fun initialSnapshotTimeSec(): Float = DEFAULT_SNAPSHOT_SEC

    /**
     * 计算粒子在 [elapsedSec] 时刻的归一化坐标与透明度权重，零分配写入缓冲。
     * [xSpeedScale] 用于窄容器（如核心试管、分流按钮）保持与底栏一致的物理流动速度（dp/s）。
     */
    fun sampleInto(
        elapsedSec: Float,
        outNormX: FloatArray,
        outNormY: FloatArray,
        outAlpha: FloatArray,
        particleCount: Int = specs.size,
        xSpeedScale: Float = 1f
    ) {
        val count = particleCount.coerceIn(0, specs.size)
        for (i in 0 until count) {
            val s = specs[i]
            val rawX = s.baseX + elapsedSec * s.speedX * xSpeedScale
            val wrapped = ((rawX % TRACK_WIDTH) + TRACK_WIDTH) % TRACK_WIDTH
            val normX = wrapped - TRACK_MARGIN
            val edgeFade = smoothstep(-0.04f, 0.06f, normX) * smoothstep(1.04f, 0.94f, normX)

            val arcAngle = TWO_PI * (normX * s.arcFreq + elapsedSec * s.verticalFreq) + s.phase
            val normY = (s.baseY + s.arcAmp * sin(arcAngle)).coerceIn(0.10f, 0.90f)

            val twinkle = 0.67f + 0.33f * sin(TWO_PI * s.twinkleFreq * elapsedSec + s.twinklePhase)
            outNormX[i] = normX
            outNormY[i] = normY
            outAlpha[i] = (s.brightness * edgeFade * twinkle).coerceIn(0f, 1f)
        }
    }

    internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge0 == edge1) return 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    internal const val MAX_NODE_LINKS = MAX_LINKS_PER_NODE
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
internal fun rememberParticleAnimationClockSec(reduceMotion: Boolean): Float {
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

    var elapsedSec by remember { mutableFloatStateOf(NavParticleField.initialSnapshotTimeSec()) }
    LaunchedEffect(reduceMotion, isResumed) {
        if (reduceMotion || !isResumed) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val deltaSec = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    elapsedSec += deltaSec
                }
                lastNanos = frameNanos
            }
        }
    }
    return elapsedSec
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlueParticleConstellation(
    elapsedSec: Float,
    particleCount: Int,
    referenceWidthDp: Float,
    normXBuf: FloatArray,
    normYBuf: FloatArray,
    alphaBuf: FloatArray,
    pxBuf: FloatArray,
    pyBuf: FloatArray,
    degreeBuf: IntArray,
    linkMaxDistDp: Float = 44f
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val activeCount = particleCount.coerceIn(0, NavParticleField.specs.size)
    val speedScale = (referenceWidthDp.dp.toPx() / w).coerceIn(0.85f, 4.5f)
    NavParticleField.sampleInto(
        elapsedSec = elapsedSec,
        outNormX = normXBuf,
        outNormY = normYBuf,
        outAlpha = alphaBuf,
        particleCount = activeCount,
        xSpeedScale = speedScale
    )

    for (i in 0 until activeCount) {
        pxBuf[i] = normXBuf[i] * w
        pyBuf[i] = normYBuf[i] * h
        degreeBuf[i] = 0
    }

    val maxLinkDist = linkMaxDistDp.dp.toPx()
    val maxLinkDistSq = maxLinkDist * maxLinkDist
    val linkStrokePx = 0.65.dp.toPx()

    for (i in 0 until activeCount) {
        val specI = NavParticleField.specs[i]
        if (specI.isBokeh || alphaBuf[i] <= 0.03f || degreeBuf[i] >= NavParticleField.MAX_NODE_LINKS) {
            continue
        }
        for (j in i + 1 until activeCount) {
            val specJ = NavParticleField.specs[j]
            if (specJ.isBokeh || alphaBuf[j] <= 0.03f || degreeBuf[j] >= NavParticleField.MAX_NODE_LINKS) {
                continue
            }
            val dx = pxBuf[i] - pxBuf[j]
            val dy = pyBuf[i] - pyBuf[j]
            val distSq = dx * dx + dy * dy
            if (distSq < maxLinkDistSq && distSq > 1f) {
                val dist = sqrt(distSq)
                val falloff = 1f - (dist / maxLinkDist)
                val lineAlpha = (falloff * 0.30f * (0.50f + 0.50f * min(alphaBuf[i], alphaBuf[j])))
                    .coerceIn(0f, 0.30f)
                if (lineAlpha > 0.01f) {
                    drawLine(
                        color = DarkNavPalette.LinkBlue.copy(alpha = lineAlpha),
                        start = Offset(pxBuf[i], pyBuf[i]),
                        end = Offset(pxBuf[j], pyBuf[j]),
                        strokeWidth = linkStrokePx,
                        cap = StrokeCap.Round
                    )
                    degreeBuf[i]++
                    degreeBuf[j]++
                    if (degreeBuf[i] >= NavParticleField.MAX_NODE_LINKS) break
                }
            }
        }
    }

    for (i in 0 until activeCount) {
        val alpha = alphaBuf[i]
        if (alpha <= 0.02f) continue
        val spec = NavParticleField.specs[i]
        val pos = Offset(pxBuf[i], pyBuf[i])
        val radiusPx = spec.radiusDp.dp.toPx()
        val baseColor = NavParticleField.colorForMix(spec.hueMix)

        drawCircle(
            color = baseColor.copy(alpha = (alpha * 0.18f).coerceIn(0f, 0.28f)),
            radius = radiusPx * 2.4f,
            center = pos
        )
        if (!spec.isBokeh) {
            drawCircle(
                color = baseColor.copy(alpha = alpha.coerceIn(0f, 0.92f)),
                radius = radiusPx,
                center = pos
            )
        } else {
            drawCircle(
                color = baseColor.copy(alpha = (alpha * 0.42f).coerceIn(0f, 0.42f)),
                radius = radiusPx * 1.45f,
                center = pos
            )
        }
    }
}

/**
 * 复用蓝色星链粒子画布（用于核心玻璃试管内部 48 颗粒子，以及分流模式选中态 18 颗粒子）
 */
@Composable
internal fun BlueConstellationParticlesCanvas(
    particleCount: Int,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    referenceWidthDp: Float = 400f
) {
    val count = particleCount.coerceIn(1, NavParticleField.specs.size)
    val effectiveReduce = reduceMotion
    val displayedCount = if (effectiveReduce) (count * 0.16f).toInt().coerceAtLeast(3) else count
    val elapsedSec = rememberParticleAnimationClockSec(reduceMotion = effectiveReduce)

    val normXBuf = remember(displayedCount) { FloatArray(displayedCount) }
    val normYBuf = remember(displayedCount) { FloatArray(displayedCount) }
    val alphaBuf = remember(displayedCount) { FloatArray(displayedCount) }
    val pxBuf = remember(displayedCount) { FloatArray(displayedCount) }
    val pyBuf = remember(displayedCount) { FloatArray(displayedCount) }
    val degreeBuf = remember(displayedCount) { IntArray(displayedCount) }

    Canvas(modifier = modifier) {
        drawBlueParticleConstellation(
            elapsedSec = elapsedSec,
            particleCount = displayedCount,
            referenceWidthDp = referenceWidthDp,
            normXBuf = normXBuf,
            normYBuf = normYBuf,
            alphaBuf = alphaBuf,
            pxBuf = pxBuf,
            pyBuf = pyBuf,
            degreeBuf = degreeBuf
        )
    }
}

/**
 * 底部悬浮胶囊导航栏背景层：深海军蓝玻璃底板 + 内外双层微光描边 + 104 颗高速星链粒子 + 柔和蓝玻璃选中高光
 */
@Composable
internal fun DarkParticleDockBackdrop(
    animatedSelectedIndex: Float,
    tabCount: Int,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val elapsedSec = rememberParticleAnimationClockSec(reduceMotion = reduceMotion)
    val count = if (reduceMotion) 14 else NavParticleField.specs.size

    val normXBuf = remember(count) { FloatArray(count) }
    val normYBuf = remember(count) { FloatArray(count) }
    val alphaBuf = remember(count) { FloatArray(count) }
    val pxBuf = remember(count) { FloatArray(count) }
    val pyBuf = remember(count) { FloatArray(count) }
    val degreeBuf = remember(count) { IntArray(count) }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val capsuleCorner = CornerRadius(h / 2f, h / 2f)

        // 1. 深海军蓝玻璃主底板 (.nav-shell)
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

        // 顶部细腻高光反射 + 内层 4dp 边框底板 (.nav-shell::before)
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(DarkNavPalette.DockSheenTop, Color.Transparent),
                startY = 0f,
                endY = h * 0.48f
            ),
            cornerRadius = capsuleCorner
        )
        val innerInset = 4.dp.toPx()
        val innerH = (h - innerInset * 2f).coerceAtLeast(1f)
        val innerCorner = CornerRadius(innerH / 2f, innerH / 2f)
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x9918293C), Color(0x7A08111D)),
                startY = innerInset,
                endY = innerInset + innerH
            ),
            topLeft = Offset(innerInset, innerInset),
            size = Size((w - innerInset * 2f).coerceAtLeast(1f), innerH),
            cornerRadius = innerCorner
        )
        drawRoundRect(
            color = DarkNavPalette.DockInnerBevelTop,
            topLeft = Offset(innerInset, innerInset),
            size = Size((w - innerInset * 2f).coerceAtLeast(1f), innerH),
            cornerRadius = innerCorner,
            style = Stroke(width = 0.85.dp.toPx())
        )

        // 2. 选中入口的蓝色玻璃滑动胶囊高光（置于粒子星链下方，确保选区内外星链光效均通透清晰）
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

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        DarkNavPalette.PillGlowCenter,
                        DarkNavPalette.PillGlowMid,
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = pillWidth * 0.82f
                ),
                radius = pillWidth * 0.82f,
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
                    end = Offset(pillLeft + pillWidth * 0.8f, innerPad + pillHeight)
                ),
                topLeft = Offset(pillLeft, innerPad),
                size = Size(pillWidth, pillHeight),
                cornerRadius = pillCorner
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(DarkNavPalette.PillRimTop, DarkNavPalette.PillRimBottom),
                    start = Offset(pillLeft, innerPad),
                    end = Offset(pillLeft + pillWidth, innerPad + pillHeight)
                ),
                topLeft = Offset(pillLeft, innerPad),
                size = Size(pillWidth, pillHeight),
                cornerRadius = pillCorner,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // 3. 104 颗持续流动蓝色粒子与星链连线（位于选中高光之上、图标文字之下）
        drawBlueParticleConstellation(
            elapsedSec = elapsedSec,
            particleCount = count,
            referenceWidthDp = 400f,
            normXBuf = normXBuf,
            normYBuf = normYBuf,
            alphaBuf = alphaBuf,
            pxBuf = pxBuf,
            pyBuf = pyBuf,
            degreeBuf = degreeBuf
        )

        // 4. 外缘蓝色玻璃主描边
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    DarkNavPalette.DockBorderTop,
                    DarkNavPalette.DockBorderMid,
                    DarkNavPalette.DockBorderBottom
                )
            ),
            cornerRadius = capsuleCorner,
            style = Stroke(width = 1.dp.toPx())
        )
    }
}
