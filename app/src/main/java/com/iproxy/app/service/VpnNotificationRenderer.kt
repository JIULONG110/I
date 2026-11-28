package com.iproxy.app.service

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.iproxy.app.R
import com.iproxy.app.core.util.FormatUtils
import com.iproxy.app.ui.components.DarkNavPalette
import com.iproxy.app.ui.components.NavParticleField
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class VpnNotificationCopy(
    val compactTitle: String,
    val expandedTitle: String,
    val statusBadge: String,
    val speedLine: String,
    val footerSubtitle: String,
    val actionLabel: String,
    val compactActionLabel: String,
    val fallbackText: String
)

internal object VpnNotificationFormatter {

    fun formatConnecting(nodeName: String?, proxyOnlyMode: Boolean): VpnNotificationCopy {
        val cleanName = nodeName?.takeIf { it.isNotBlank() } ?: "安全加密通道"
        val modeLabel = if (proxyOnlyMode) "纯代理" else "VPN"
        return VpnNotificationCopy(
            compactTitle = "I · 正在连接 $cleanName",
            expandedTitle = "I · $cleanName",
            statusBadge = "$modeLabel · 连接中",
            speedLine = "正在初始化 Xray 内核与加密隧道...",
            footerSubtitle = "连接建立中 · 请稍候",
            actionLabel = "断开连接",
            compactActionLabel = "取消",
            fallbackText = "正在连接 · 准备启动代理内核"
        )
    }

    fun formatConnected(
        nodeName: String,
        protocolBadge: String,
        uplinkBps: Long,
        downlinkBps: Long,
        proxyOnlyMode: Boolean
    ): VpnNotificationCopy {
        val modeLabel = if (proxyOnlyMode) "纯代理" else "VPN"
        val cleanProto = protocolBadge.ifBlank { "安全节点" }
        val speedSummary = "↑ ${FormatUtils.formatSpeed(uplinkBps)}   ↓ ${FormatUtils.formatSpeed(downlinkBps)} · $modeLabel"
        return VpnNotificationCopy(
            compactTitle = "I · $nodeName",
            expandedTitle = "I · $nodeName",
            statusBadge = "$modeLabel · 已连接",
            speedLine = speedSummary,
            footerSubtitle = "$cleanProto · 安全通道运行中",
            actionLabel = "断开连接",
            compactActionLabel = "断开",
            fallbackText = "$speedSummary · 已连接"
        )
    }
}

/**
 * Android 通知栏 3D 深海玻璃实体试管与实时流动蓝色星链粒子渲染器（Runtime Canvas 绘制）。
 * 复用了应用内 104 粒子黄金分布星链物理引擎，确保通知栏视觉与图 1 完全统一，同时严控位图尺寸（总计 < 210 KB）
 * 以符合 Android 12–15 Binder IPC 限制并保持低耗电。
 */
internal class VpnNotificationRenderer {

    private val normXBuf = FloatArray(NavParticleField.specs.size)
    private val normYBuf = FloatArray(NavParticleField.specs.size)
    private val alphaBuf = FloatArray(NavParticleField.specs.size)
    private val pxBuf = FloatArray(NavParticleField.specs.size)
    private val pyBuf = FloatArray(NavParticleField.specs.size)
    private val degreeBuf = IntArray(NavParticleField.specs.size)

    @Volatile
    private var cachedLogoBadge: Bitmap? = null

    private fun getOrCreateLogoBadge(context: Context): Bitmap? {
        cachedLogoBadge?.let { return it }
        return runCatching {
            val raw = BitmapFactory.decodeResource(context.resources, R.drawable.brand_logo) ?: return null
            val size = LOGO_BMP_SIZE
            val scaled = if (raw.width == size && raw.height == size) {
                raw
            } else {
                Bitmap.createScaledBitmap(raw, size, size, true).also {
                    if (it !== raw) raw.recycle()
                }
            }
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val rect = RectF(1.5f, 1.5f, size - 1.5f, size - 1.5f)
            val radius = size * 0.30f
            val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            canvas.drawRoundRect(rect, radius, radius, shaderPaint)
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.2f
                shader = LinearGradient(
                    0f, 0f, size.toFloat(), size.toFloat(),
                    0xCC80D8FF.toInt(),
                    0x664095E6,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(rect, radius, radius, rimPaint)
            if (scaled !== out) scaled.recycle()
            cachedLogoBadge = out
            out
        }.getOrNull()
    }

    fun buildCompactRemoteViews(
        context: Context,
        copy: VpnNotificationCopy,
        elapsedAnimSec: Float,
        stopIntent: PendingIntent
    ): RemoteViews {
        val bg = renderGlassBackdropBitmap(
            widthPx = COMPACT_BMP_WIDTH,
            heightPx = COMPACT_BMP_HEIGHT,
            elapsedSec = elapsedAnimSec,
            particleCount = 48,
            cornerRadiusPx = COMPACT_BMP_HEIGHT * 0.44f,
            isCompact = true
        )
        val logoBmp = getOrCreateLogoBadge(context)
        return RemoteViews(context.packageName, R.layout.notification_vpn_compact).apply {
            setImageViewBitmap(R.id.iv_particle_bg, bg)
            if (logoBmp != null) {
                setImageViewBitmap(R.id.iv_brand_logo, logoBmp)
            }
            setTextViewText(R.id.tv_title, copy.compactTitle)
            setTextViewText(R.id.tv_stats, copy.speedLine)
            setTextViewText(R.id.btn_disconnect, copy.compactActionLabel)
            setOnClickPendingIntent(R.id.btn_disconnect, stopIntent)
        }
    }

    fun buildExpandedRemoteViews(
        context: Context,
        copy: VpnNotificationCopy,
        elapsedAnimSec: Float,
        stopIntent: PendingIntent
    ): RemoteViews {
        val bg = renderGlassBackdropBitmap(
            widthPx = EXPANDED_BMP_WIDTH,
            heightPx = EXPANDED_BMP_HEIGHT,
            elapsedSec = elapsedAnimSec,
            particleCount = 82,
            cornerRadiusPx = 22f,
            isCompact = false
        )
        val logoBmp = getOrCreateLogoBadge(context)
        return RemoteViews(context.packageName, R.layout.notification_vpn_expanded).apply {
            setImageViewBitmap(R.id.iv_particle_bg, bg)
            if (logoBmp != null) {
                setImageViewBitmap(R.id.iv_brand_logo, logoBmp)
            }
            setTextViewText(R.id.tv_title, copy.expandedTitle)
            setTextViewText(R.id.tv_status_badge, copy.statusBadge)
            setTextViewText(R.id.tv_stats, copy.speedLine)
            setTextViewText(R.id.tv_subtitle, copy.footerSubtitle)
            setTextViewText(R.id.btn_disconnect, copy.actionLabel)
            setOnClickPendingIntent(R.id.btn_disconnect, stopIntent)
        }
    }

    internal fun renderGlassBackdropBitmap(
        widthPx: Int,
        heightPx: Int,
        elapsedSec: Float,
        particleCount: Int,
        cornerRadiusPx: Float,
        isCompact: Boolean
    ): Bitmap {
        val w = widthPx.coerceAtLeast(32)
        val h = heightPx.coerceAtLeast(16)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val wf = w.toFloat()
        val hf = h.toFloat()
        val cr = cornerRadiusPx.coerceAtMost(min(wf, hf) * 0.48f)
        val outerRect = RectF(1f, 1f, wf - 1f, hf - 1f)
        val clipPath = Path().apply { addRoundRect(outerRect, cr, cr, Path.Direction.CW) }

        canvas.save()
        canvas.clipPath(clipPath)

        // 1. 图 1 同款深海军蓝通透玻璃底色
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, hf,
                intArrayOf(0xF5142334.toInt(), 0xF70E1A29.toInt(), 0xFA08111E.toInt()),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(outerRect, bgPaint)

        // 2. 左侧激活区钴蓝立体胶囊光晕（呼应图 1 左侧激活态蓝光玻璃高亮）
        val glowCenterX = if (isCompact) wf * 0.16f else wf * 0.22f
        val glowCenterY = hf * 0.42f
        val glowRadius = (hf * 1.45f).coerceAtLeast(32f)
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                glowCenterX, glowCenterY, glowRadius,
                intArrayOf(0x6E1F8EFF, 0x331454A6, 0x00000000),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(glowCenterX, glowCenterY, glowRadius, glowPaint)

        // 3. 顶部弯月玻璃折射高光
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, hf * 0.44f,
                0x28CBEFFF,
                0x00000000,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, wf, hf * 0.44f, sheenPaint)

        // 4. 实时运行时生成的快速流动蓝色颗粒与星链网
        drawNotificationConstellation(canvas, wf, hf, elapsedSec, particleCount, isCompact)

        // 5. 内层 3D 玻璃管壁凹陷高光环
        val bevelInset = if (isCompact) 3.2f else 4.2f
        val innerRect = RectF(
            outerRect.left + bevelInset,
            outerRect.top + bevelInset,
            outerRect.right - bevelInset,
            outerRect.bottom - bevelInset
        )
        val innerCorner = (cr - bevelInset * 0.7f).coerceAtLeast(2f)
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
            shader = LinearGradient(
                0f, innerRect.top, 0f, innerRect.bottom,
                0x4D78C8FF,
                0x221B4C84,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(innerRect, innerCorner, innerCorner, innerPaint)

        canvas.restore()

        // 6. 外圈立体蓝青玻璃主边缘
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            shader = LinearGradient(
                0f, 0f, wf, hf,
                intArrayOf(0xC87AD5FF.toInt(), 0x7E588DC0, 0x9E4A9FE8.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(outerRect, cr, cr, borderPaint)

        return bmp
    }

    private fun drawNotificationConstellation(
        canvas: Canvas,
        w: Float,
        h: Float,
        elapsedSec: Float,
        particleCount: Int,
        isCompact: Boolean
    ) {
        val activeCount = particleCount.coerceIn(1, NavParticleField.specs.size)
        val speedScale = if (isCompact) 1.35f else 1.25f
        NavParticleField.sampleInto(
            elapsedSec = elapsedSec,
            outNormX = normXBuf,
            outNormY = normYBuf,
            outAlpha = alphaBuf,
            particleCount = activeCount,
            xSpeedScale = speedScale,
            timeOffsetSec = if (isCompact) 0.5f else 0f
        )

        for (i in 0 until activeCount) {
            pxBuf[i] = normXBuf[i] * w
            pyBuf[i] = normYBuf[i] * h
            degreeBuf[i] = 0
        }

        val maxLinkDist = if (isCompact) 42f else 50f
        val maxLinkDistSq = maxLinkDist * maxLinkDist
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.85f
            strokeCap = Paint.Cap.ROUND
        }
        val linkRgb = DarkNavPalette.LinkBlue.toArgb() and 0x00FFFFFF

        for (i in 0 until activeCount) {
            val sI = NavParticleField.specs[i]
            if (sI.isBokeh || alphaBuf[i] <= 0.03f || degreeBuf[i] >= NavParticleField.MAX_NODE_LINKS) continue
            for (j in i + 1 until activeCount) {
                val sJ = NavParticleField.specs[j]
                if (sJ.isBokeh || alphaBuf[j] <= 0.03f || degreeBuf[j] >= NavParticleField.MAX_NODE_LINKS) continue
                val dx = pxBuf[i] - pxBuf[j]
                val dy = pyBuf[i] - pyBuf[j]
                val distSq = dx * dx + dy * dy
                if (distSq < maxLinkDistSq && distSq > 1f) {
                    val dist = sqrt(distSq)
                    val falloff = 1f - (dist / maxLinkDist)
                    val lineAlpha = (falloff * 0.42f * (0.48f + 0.52f * min(alphaBuf[i], alphaBuf[j])))
                        .coerceIn(0f, 0.44f)
                    val aByte = (lineAlpha * 255f).roundToInt().coerceIn(0, 255)
                    if (aByte > 2) {
                        linePaint.color = (aByte shl 24) or linkRgb
                        canvas.drawLine(pxBuf[i], pyBuf[i], pxBuf[j], pyBuf[j], linePaint)
                        degreeBuf[i]++
                        degreeBuf[j]++
                        if (degreeBuf[i] >= NavParticleField.MAX_NODE_LINKS) break
                    }
                }
            }
        }

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val iceRgb = DarkNavPalette.ParticleCoreIce.toArgb() and 0x00FFFFFF

        for (i in 0 until activeCount) {
            val alpha = alphaBuf[i]
            if (alpha <= 0.02f) continue
            val spec = NavParticleField.specs[i]
            val cx = pxBuf[i]
            val cy = pyBuf[i]
            val r = spec.radiusDp * (if (isCompact) 1.05f else 1.28f)
            val colorRgb = NavParticleField.colorForMix(spec.hueMix).toArgb() and 0x00FFFFFF

            // 外光晕
            val haloA = (alpha * 0.28f * 255f).roundToInt().coerceIn(0, 96)
            dotPaint.color = (haloA shl 24) or colorRgb
            canvas.drawCircle(cx, cy, r * 2.5f, dotPaint)

            if (!spec.isBokeh) {
                val coreA = (alpha * 0.94f * 255f).roundToInt().coerceIn(0, 245)
                dotPaint.color = (coreA shl 24) or colorRgb
                canvas.drawCircle(cx, cy, r, dotPaint)

                if (alpha > 0.44f) {
                    val iceA = (alpha * 0.85f * 255f).roundToInt().coerceIn(0, 235)
                    dotPaint.color = (iceA shl 24) or iceRgb
                    canvas.drawCircle(cx, cy, (r * 0.48f).coerceAtLeast(0.75f), dotPaint)
                }
            } else {
                val bokehA = (alpha * 0.46f * 255f).roundToInt().coerceIn(0, 128)
                dotPaint.color = (bokehA shl 24) or colorRgb
                canvas.drawCircle(cx, cy, r * 1.48f, dotPaint)
            }
        }
    }

    private companion object {
        private const val COMPACT_BMP_WIDTH = 320
        private const val COMPACT_BMP_HEIGHT = 48
        private const val EXPANDED_BMP_WIDTH = 340
        private const val EXPANDED_BMP_HEIGHT = 112
        private const val LOGO_BMP_SIZE = 64
    }
}
