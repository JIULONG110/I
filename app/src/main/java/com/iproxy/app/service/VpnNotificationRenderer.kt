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
import com.iproxy.app.ui.components.RisingTubeParticleField
import kotlin.math.min

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
 * Android 通知栏 3D 深邃紫-洋红-电蓝实体玻璃与等比例快速上冲流光粒子渲染器（Runtime Canvas 绘制）。
 * 严控位图总量（< 220 KB）与 350ms 亮屏节拍，兼顾流畅实时粒子与低耗电及 Android 12–15 通知 Binder 限制。
 */
internal class VpnNotificationRenderer {

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
                    0xDDE4C6FF.toInt(),
                    0xA34BE8FF.toInt(),
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
            cornerRadiusPx = COMPACT_BMP_HEIGHT * 0.5f,
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
            cornerRadiusPx = 24f,
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
        cornerRadiusPx: Float,
        isCompact: Boolean
    ): Bitmap {
        val w = widthPx.coerceAtLeast(32)
        val h = heightPx.coerceAtLeast(16)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val wf = w.toFloat()
        val hf = h.toFloat()
        val cr = cornerRadiusPx.coerceAtMost(min(wf, hf) * 0.5f)
        val outerRect = RectF(1f, 1f, wf - 1f, hf - 1f)
        val clipPath = Path().apply { addRoundRect(outerRect, cr, cr, Path.Direction.CW) }

        canvas.save()
        canvas.clipPath(clipPath)

        // 1. 略压暗的深紫靛蓝实体玻璃底板（保持紫/洋红/电蓝粒子浓烈且不抢文本对比度）
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, hf,
                intArrayOf(0xF7110D24.toInt(), 0xFA090717.toInt(), 0xFC070A18.toInt()),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(outerRect, bgPaint)

        // 2. 内部底部紫/洋红/电蓝折射焦点光晕
        val glowCenterX = wf * 0.42f
        val glowCenterY = hf * 0.88f
        val glowRadius = (hf * 1.45f).coerceAtLeast(36f)
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                glowCenterX, glowCenterY, glowRadius,
                intArrayOf(0x6E832FFF.toInt(), 0x3CE82EC8.toInt(), 0x241F74FF.toInt(), 0x00000000),
                floatArrayOf(0f, 0.42f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(glowCenterX, glowCenterY, glowRadius, glowPaint)

        // 3. 运行时实时生成：按通知面积等比例缩放的快速上冲紫/洋红/电蓝 + 粉白/青色流光粒子
        drawRisingNotificationPlasma(canvas, wf, hf, elapsedSec, isCompact)

        // 4. 顶部石英玻璃弧形反光 + 内缩立体玻璃内壁描边
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, hf * 0.40f,
                0x2CECE2FF.toInt(),
                0x00000000,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, wf, hf * 0.40f, sheenPaint)

        val bevelInset = if (isCompact) 3.0f else 4.0f
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
                0x5EE4D4FF.toInt(),
                0x303B7CE6.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(innerRect, innerCorner, innerCorner, innerPaint)

        canvas.restore()

        // 5. 外层 3D 紫-洋红-电蓝-青玻璃主外边
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            shader = LinearGradient(
                0f, 0f, wf, hf,
                intArrayOf(0xD9D4BFFF.toInt(), 0xA8C84CE8.toInt(), 0xC44BE8FF.toInt()),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(outerRect, cr, cr, borderPaint)

        return bmp
    }

    private fun drawRisingNotificationPlasma(
        canvas: Canvas,
        w: Float,
        h: Float,
        elapsedSec: Float,
        isCompact: Boolean
    ) {
        val counts = RisingTubeParticleField.scaleParticleCounts(
            widthDp = w,
            heightDp = h,
            isHeroTube = false,
            densityMultiplier = if (isCompact) 0.56f else 0.48f,
            reduceMotion = false
        )
        val ySpeedScale = if (isCompact) 2.2f else 1.55f
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        val largeSpecs = RisingTubeParticleField.largeSpecs
        val largeLimit = counts.largeCount.coerceIn(0, largeSpecs.size)
        for (i in 0 until largeLimit) {
            val spec = largeSpecs[i]
            val (nx, ny, alpha) = RisingTubeParticleField.sampleSpecAt(
                spec = spec,
                elapsedSec = elapsedSec,
                timeOffsetSec = if (isCompact) 0.5f else 0f,
                ySpeedScale = ySpeedScale,
                columnConcentrated = false
            )
            if (alpha <= 0.03f) continue

            val cx = nx * w
            val cy = ny * h
            val r = spec.radiusDp * (if (isCompact) 0.82f else 0.96f)
            val plasmaRgb = RisingTubeParticleField.plasmaColorForMix(spec.hueMix).toArgb() and 0x00FFFFFF
            val coreRgb = RisingTubeParticleField.coreColorForPreference(spec.corePreference).toArgb() and 0x00FFFFFF

            val tailLen = (spec.trailFactor * h * spec.speedY * 1.45f).coerceIn(r * 1.0f, h * 0.14f)
            val tailAlpha = (alpha * 0.24f * 255f).toInt().coerceIn(0, 72)
            streakPaint.color = (tailAlpha shl 24) or plasmaRgb
            streakPaint.strokeWidth = (r * 0.9f).coerceAtLeast(1.0f)
            canvas.drawLine(cx, cy + tailLen, cx, cy, streakPaint)

            val haloA = (alpha * 0.24f * 255f).toInt().coerceIn(0, 82)
            dotPaint.color = (haloA shl 24) or plasmaRgb
            canvas.drawCircle(cx, cy, r * 2.35f, dotPaint)

            val bodyA = (alpha * 0.78f * 255f).toInt().coerceIn(0, 200)
            dotPaint.color = (bodyA shl 24) or plasmaRgb
            canvas.drawCircle(cx, cy, r, dotPaint)

            val coreA = (alpha * 0.88f * 255f).toInt().coerceIn(0, 225)
            dotPaint.color = (coreA shl 24) or coreRgb
            canvas.drawCircle(cx, cy, (r * 0.44f).coerceAtLeast(0.75f), dotPaint)
        }

        val fineSpecs = RisingTubeParticleField.fineSpecs
        val fineLimit = counts.fineCount.coerceIn(0, fineSpecs.size)
        for (i in 0 until fineLimit) {
            val spec = fineSpecs[i]
            val (nx, ny, alpha) = RisingTubeParticleField.sampleSpecAt(
                spec = spec,
                elapsedSec = elapsedSec,
                timeOffsetSec = if (isCompact) 0.5f else 0f,
                ySpeedScale = ySpeedScale,
                columnConcentrated = false
            )
            if (alpha <= 0.03f) continue

            val cx = nx * w
            val cy = ny * h
            val r = spec.radiusDp * (if (isCompact) 0.86f else 0.96f)
            val plasmaRgb = RisingTubeParticleField.plasmaColorForMix(spec.hueMix).toArgb() and 0x00FFFFFF
            val coreRgb = RisingTubeParticleField.coreColorForPreference(spec.corePreference).toArgb() and 0x00FFFFFF

            if (spec.trailFactor > 0.009f) {
                val tailLen = (spec.trailFactor * h * spec.speedY * 1.55f).coerceIn(1.8f, h * 0.12f)
                val lineA = (alpha * 0.58f * 255f).toInt().coerceIn(0, 150)
                streakPaint.color = (lineA shl 24) or plasmaRgb
                streakPaint.strokeWidth = (r * 1.4f).coerceAtLeast(0.95f)
                canvas.drawLine(cx, cy + tailLen, cx, cy, streakPaint)

                val headA = (alpha * 0.88f * 255f).toInt().coerceIn(0, 225)
                dotPaint.color = (headA shl 24) or coreRgb
                canvas.drawCircle(cx, cy, (r * 0.9f).coerceAtLeast(0.75f), dotPaint)
            } else {
                val dotA = (alpha * 0.78f * 255f).toInt().coerceIn(0, 200)
                dotPaint.color = (dotA shl 24) or plasmaRgb
                canvas.drawCircle(cx, cy, r, dotPaint)
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
