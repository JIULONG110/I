package com.iproxy.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPasteGo
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iproxy.app.core.util.FormatUtils
import com.iproxy.app.data.model.AppSettings
import com.iproxy.app.data.model.RoutingMode
import com.iproxy.app.data.model.ServerNode
import com.iproxy.app.data.model.VpnRuntimeState
import com.iproxy.app.data.model.VpnStatus
import com.iproxy.app.ui.components.BlueConstellationParticlesCanvas
import com.iproxy.app.ui.components.LiquidGlassCard
import com.iproxy.app.ui.components.ProtoAccentSky
import com.iproxy.app.ui.components.ProtoDividerBlue
import com.iproxy.app.ui.components.ProtoTextMuted
import com.iproxy.app.ui.components.ProtoTextPrimary
import com.iproxy.app.ui.components.ProtoTextSecondary
import com.iproxy.app.ui.components.ProtoTextSubtle
import com.iproxy.app.ui.components.PrototypeRoutingModeCard
import com.iproxy.app.ui.components.rememberReduceMotionState
import com.iproxy.app.ui.theme.ConnectedGreen
import com.iproxy.app.ui.theme.ErrorRed
import com.iproxy.app.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 首页连接仪表盘：1:1 对标原型 3D 蓝色玻璃反应舱、流动粒子、双栏网速、当前节点与分流卡
 */
@Composable
fun HomeScreen(
    runtimeState: VpnRuntimeState,
    nodes: List<ServerNode>,
    selectedNode: ServerNode?,
    nodeCount: Int,
    settings: AppSettings,
    isTestingLatency: Boolean,
    onToggleConnect: () -> Unit,
    onSelectNode: (String) -> Unit,
    onOpenNodesTab: () -> Unit,
    onTestCurrentNode: (String) -> Unit,
    onChangeRoutingMode: (RoutingMode) -> Unit,
    onImportClipboard: () -> Unit,
    onOpenQrScan: () -> Unit,
    onOpenAddSub: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val isConnected = runtimeState.status == VpnStatus.CONNECTED
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var showNodeModal by remember { mutableStateOf(false) }

    LaunchedEffect(isConnected, runtimeState.connectedSinceMs) {
        if (isConnected && runtimeState.connectedSinceMs > 0L) {
            while (true) {
                elapsedMs = (System.currentTimeMillis() - runtimeState.connectedSinceMs).coerceAtLeast(0L)
                delay(1000L)
            }
        } else {
            elapsedMs = 0L
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compactHeight = maxHeight < 780.dp
        val narrowWidth = maxWidth < 360.dp
        val sidePadding = if (narrowWidth) 13.dp else 18.dp
        val heroSidePadding = (sidePadding - 14.dp).coerceAtLeast(2.dp)
        val heroHeight = (maxHeight * 0.313f).coerceIn(
            if (compactHeight) 224.dp else 252.dp,
            318.dp
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部留白对应原型的干净顶栏间距（已移除左右两个小试管/Logo/状态角标）
            Spacer(modifier = Modifier.height(if (compactHeight) 14.dp else 30.dp))

            // 中央实体 3D 蓝色玻璃反应舱 + 内部 48 颗持续流动蓝色粒子（开关两态亮度与动效完全一致，不显示文字与图标）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = heroSidePadding)
                    .padding(bottom = 6.dp)
                    .height(heroHeight)
            ) {
                ReactorHeroCylinder(
                    status = runtimeState.status,
                    onClick = onToggleConnect,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sidePadding)
            ) {
                // 实时上传 / 下载流量统计卡
                TrafficSummaryCard(
                    runtimeState = runtimeState,
                    compactHeight = compactHeight,
                    onClick = {
                        val tip = when (runtimeState.status) {
                            VpnStatus.CONNECTED -> "安全通道运行中 · 已持续 ${FormatUtils.formatDuration(elapsedMs)}"
                            VpnStatus.CONNECTING -> "正在建立安全加密通道..."
                            VpnStatus.ERROR -> runtimeState.lastError ?: "连接出现异常，请检查节点设置"
                            VpnStatus.DISCONNECTED -> "当前未连接，上传与下载速率已归零"
                        }
                        onShowMessage(tip)
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 当前节点与测速卡片
                CurrentNodeCard(
                    selectedNode = selectedNode,
                    nodeCount = nodeCount,
                    compactHeight = compactHeight,
                    narrowWidth = narrowWidth,
                    isTestingLatency = isTestingLatency,
                    onCardClick = { showNodeModal = true },
                    onSpeedTestClick = {
                        if (selectedNode != null) {
                            onTestCurrentNode(selectedNode.id)
                        } else {
                            showNodeModal = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 智能分流模式卡（选中按钮使用与底栏一致的蓝色流动粒子与星链连线）
                PrototypeRoutingModeCard(
                    routingMode = settings.routingMode,
                    onSelectMode = onChangeRoutingMode
                )

                if (!runtimeState.lastError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        accentBorder = ErrorRed
                    ) {
                        Text(
                            text = "连接提示：${runtimeState.lastError}",
                            color = Color(0xFFFCA5A5),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.navigationBarsPadding())
                Spacer(modifier = Modifier.height(112.dp))
            }
        }
    }

    if (showNodeModal) {
        NodeSelectorBottomSheet(
            nodes = nodes,
            selectedNodeId = selectedNode?.id,
            onDismiss = { showNodeModal = false },
            onSelectNode = { id ->
                onSelectNode(id)
                showNodeModal = false
            },
            onOpenNodesTab = {
                showNodeModal = false
                onOpenNodesTab()
            },
            onImportClipboard = {
                showNodeModal = false
                onImportClipboard()
            },
            onOpenQrScan = {
                showNodeModal = false
                onOpenQrScan()
            },
            onOpenAddSub = {
                showNodeModal = false
                onOpenAddSub()
            }
        )
    }
}

@Composable
private fun ReactorHeroCylinder(
    status: VpnStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotionState()
    val reactorBitmap: ImageBitmap? = remember(context) {
        runCatching {
            context.assets.open("reactor-crop.png").use { stream ->
                BitmapFactory.decodeStream(stream)?.let { raw ->
                    featherReactorEdgesToTransparent(raw).asImageBitmap()
                }
            }
        }.getOrNull()
    }

    val stateLabel = when (status) {
        VpnStatus.CONNECTED -> "已连接"
        VpnStatus.CONNECTING -> "连接中"
        VpnStatus.ERROR -> "异常"
        VpnStatus.DISCONNECTED -> "未连接"
    }

    BoxWithConstraints(modifier = modifier) {
        val heroW = maxWidth
        val heroH = maxHeight

        // 底层环境蓝光晕（.hero::after）+ 原生高分辨率玻璃反应舱原图（mix-blend-mode: screen, opacity: 0.92）
        Canvas(modifier = Modifier.fillMaxSize()) {
            val glowCenter = Offset(size.width * 0.5f, size.height * 0.64f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x360A55C5), Color.Transparent),
                    center = glowCenter,
                    radius = size.minDimension * 0.68f
                ),
                radius = size.minDimension * 0.68f,
                center = glowCenter
            )

            reactorBitmap?.let { bmp ->
                val scale = min(size.width / bmp.width.toFloat(), size.height / bmp.height.toFloat())
                val dstW = (bmp.width * scale).roundToInt().coerceAtLeast(1)
                val dstH = (bmp.height * scale).roundToInt().coerceAtLeast(1)
                val dstX = ((size.width - dstW) * 0.5f).roundToInt()
                val dstY = ((size.height - dstH) * 0.5f).roundToInt()
                drawImage(
                    image = bmp,
                    dstOffset = IntOffset(dstX, dstY),
                    dstSize = IntSize(dstW, dstH),
                    alpha = 0.92f,
                    blendMode = BlendMode.Screen,
                    filterQuality = FilterQuality.High
                )
            }
        }

        // 反应舱中央 48 颗蓝色星链粒子（位于管体内，开关两态亮度与流动一致）
        BlueConstellationParticlesCanvas(
            particleCount = 48,
            reduceMotion = reduceMotion,
            referenceWidthDp = 380f,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = heroH * 0.14f)
                .size(width = heroW * 0.50f, height = heroH * 0.74f)
                .clip(RoundedCornerShape(38.dp))
        )

        // 透明触控热区（仅响应点击与无障碍语义，不渲染任何按钮图标或文字）
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(
                    width = (heroW * 0.58f).coerceAtMost(250.dp),
                    height = heroH * 0.74f
                )
                .clip(RoundedCornerShape(42.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = "玻璃试管主体，点击切换连接"
                    stateDescription = stateLabel
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClickLabel = "切换连接状态",
                    onClick = onClick
                )
        )
    }
}

@Composable
private fun TrafficSummaryCard(
    runtimeState: VpnRuntimeState,
    compactHeight: Boolean,
    onClick: () -> Unit
) {
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compactHeight) 82.dp else 91.dp),
        cornerRadius = 24.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrafficColumn(
                isUpload = true,
                label = "上传速率",
                rate = FormatUtils.formatSpeed(runtimeState.uplinkSpeedBps),
                total = FormatUtils.formatBytes(runtimeState.totalUplinkBytes),
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(54.dp)
                    .background(ProtoDividerBlue)
            )
            TrafficColumn(
                isUpload = false,
                label = "下载速率",
                rate = FormatUtils.formatSpeed(runtimeState.downlinkSpeedBps),
                total = FormatUtils.formatBytes(runtimeState.totalDownlinkBytes),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TrafficColumn(
    isUpload: Boolean,
    label: String,
    rate: String,
    total: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Canvas(modifier = Modifier.size(15.dp)) {
                val sx = size.width / 16f
                val sy = size.height / 16f
                val stroke = Stroke(width = 2.1f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawLine(
                    color = ProtoAccentSky,
                    start = Offset(8f * sx, if (isUpload) 13f * sy else 3f * sy),
                    end = Offset(8f * sx, if (isUpload) 3f * sy else 13f * sy),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
                val arrow = Path().apply {
                    if (isUpload) {
                        moveTo(4.2f * sx, 6.8f * sy)
                        lineTo(8f * sx, 3f * sy)
                        lineTo(11.8f * sx, 6.8f * sy)
                    } else {
                        moveTo(4.2f * sx, 9.2f * sy)
                        lineTo(8f * sx, 13f * sy)
                        lineTo(11.8f * sx, 9.2f * sy)
                    }
                }
                drawPath(path = arrow, color = ProtoAccentSky, style = stroke)
            }
            Text(
                text = label,
                fontSize = 13.sp,
                color = Color(0xFFC0D4EA)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = rate,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.35.sp,
            color = ProtoTextPrimary,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "总计 $total",
            fontSize = 11.sp,
            color = Color(0xFF9AABC0),
            maxLines = 1
        )
    }
}

@Composable
private fun CurrentNodeCard(
    selectedNode: ServerNode?,
    nodeCount: Int,
    compactHeight: Boolean,
    narrowWidth: Boolean,
    isTestingLatency: Boolean,
    onCardClick: () -> Unit,
    onSpeedTestClick: () -> Unit
) {
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compactHeight) 88.dp else 96.dp),
        cornerRadius = 24.dp,
        onClick = onCardClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (narrowWidth) 13.dp else 16.dp,
                    end = if (narrowWidth) 10.dp else 14.dp,
                    top = 14.dp,
                    bottom = 14.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "当前节点（共 $nodeCount 个）",
                    fontSize = 13.sp,
                    color = ProtoTextSecondary
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = selectedNode?.name ?: "暂无节点 · 点击导入",
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProtoTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(7.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x474574A4))
                            .border(1.dp, Color(0x386BA3D7), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = selectedNode?.badgeText() ?: "导入配置",
                            fontSize = 11.sp,
                            color = Color(0xFFB6C6D9),
                            maxLines = 1
                        )
                    }
                    if (selectedNode != null) {
                        val latencyText = when {
                            isTestingLatency -> "…"
                            selectedNode.latencyMs == null -> "未测速"
                            selectedNode.latencyMs <= 0L -> "超时"
                            else -> "${selectedNode.latencyMs} ms"
                        }
                        val latencyColor = when {
                            isTestingLatency -> Color(0xFFB6C6D9)
                            selectedNode.latencyMs == null -> Color(0xFFB6C6D9)
                            selectedNode.latencyMs <= 0L -> Color(0xFFFCA5A5)
                            selectedNode.latencyMs >= 500L -> WarningAmber
                            else -> Color(0xFFB6C6D9)
                        }
                        Text(
                            text = latencyText,
                            fontSize = 11.sp,
                            color = latencyColor,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            SpeedPillButton(
                label = when {
                    selectedNode == null -> "导入"
                    isTestingLatency -> "测速中"
                    else -> "测速"
                },
                isLoading = isTestingLatency && selectedNode != null,
                narrowWidth = narrowWidth,
                onClick = onSpeedTestClick
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "选择节点",
                tint = Color(0xFFB7D4F2),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SpeedPillButton(
    label: String,
    isLoading: Boolean,
    narrowWidth: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val transition = rememberInfiniteTransition(label = "speedPulse")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.36f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speedDotAlpha"
    )

    Row(
        modifier = Modifier
            .widthIn(min = if (narrowWidth) 82.dp else 94.dp)
            .height(42.dp)
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xF513263E), Color(0xF5081220))
                ),
                shape = shape
            )
            .border(1.dp, Color(0xD165ACF7), shape)
            .clickable(enabled = !isLoading, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (narrowWidth) 9.dp else 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(dotAlpha)
                    .clip(CircleShape)
                    .background(Color(0xFF58C5FF))
            )
        } else {
            Canvas(modifier = Modifier.size(16.dp)) {
                val sx = size.width / 20f
                val sy = size.height / 20f
                val stroke = Stroke(width = 1.75f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawArc(
                    color = Color(0xFFB6E9FF),
                    startAngle = 152f,
                    sweepAngle = 236f,
                    useCenter = false,
                    topLeft = Offset(3f * sx, 4f * sy),
                    size = androidx.compose.ui.geometry.Size(14f * sx, 14f * sy),
                    style = stroke
                )
                drawLine(
                    color = Color(0xFFB6E9FF),
                    start = Offset(10f * sx, 10f * sy),
                    end = Offset(14f * sx, 6f * sy),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFB6E9FF),
                    start = Offset(4f * sx, 16f * sy),
                    end = Offset(16f * sx, 16f * sy),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
                drawCircle(color = Color(0xFFB6E9FF), radius = 1.15f * sx, center = Offset(10f * sx, 10f * sy))
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = ProtoTextPrimary,
            maxLines = 1
        )
    }
}

/**
 * 将原始深黑摄影棚背景边缘柔化过渡为全透明，防止在不同设备 GPU 上因离屏合成出现矩形黑框。
 */
private fun featherReactorEdgesToTransparent(src: android.graphics.Bitmap): android.graphics.Bitmap {
    val w = src.width
    val h = src.height
    val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    if (src !== out) src.recycle()

    val halfW = w * 0.5f
    val halfH = h * 0.5f
    for (y in 0 until h) {
        val ny = kotlin.math.abs((y - halfH) / halfH)
        val yMask = if (ny <= 0.78f) 1f else {
            val t = ((ny - 0.78f) / 0.21f).coerceIn(0f, 1f)
            1f - t * t * (3f - 2f * t)
        }
        val rowOffset = y * w
        for (x in 0 until w) {
            val nx = kotlin.math.abs((x - halfW) / halfW)
            val xMask = if (nx <= 0.80f) 1f else {
                val t = ((nx - 0.80f) / 0.19f).coerceIn(0f, 1f)
                1f - t * t * (3f - 2f * t)
            }
            val c = pixels[rowOffset + x]
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            val maxCh = maxOf(r, g, b)
            // 低亮度背景黑边按通道峰值平滑柔化为透明，保留所有蓝色光粒与银蓝玻璃细节
            val darkFade = if (maxCh >= 36) 1f else (maxCh / 36f)
            val alpha = (255f * xMask * yMask * darkFade).roundToInt().coerceIn(0, 255)
            pixels[rowOffset + x] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    out.setPixels(pixels, 0, w, 0, 0, w, h)
    return out
}

@Composable
private fun NodeSelectorBottomSheet(
    nodes: List<ServerNode>,
    selectedNodeId: String?,
    onDismiss: () -> Unit,
    onSelectNode: (String) -> Unit,
    onOpenNodesTab: () -> Unit,
    onImportClipboard: () -> Unit,
    onOpenQrScan: () -> Unit,
    onOpenAddSub: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xA601040A))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.76f)
                    .clip(sheetShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFC172739), Color(0xFF08101C))
                        )
                    )
                    .border(width = 1.dp, color = Color(0x8064ADE4), shape = sheetShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color(0x8C9FBAD4))
                )
                Spacer(modifier = Modifier.height(15.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选择节点",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = ProtoTextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xD9203247))
                            .border(1.dp, Color(0x4787AFD3), CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            tint = Color(0xFFC7D8EA),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (nodes.isEmpty()) {
                        LiquidGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 21.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "暂无可用节点",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ProtoTextPrimary
                                )
                                Text(
                                    text = "您可以从剪贴板导入分享链接、扫描二维码或添加订阅链接。",
                                    fontSize = 12.sp,
                                    color = ProtoTextMuted
                                )
                                SheetQuickActionButton(
                                    icon = Icons.Rounded.ContentPasteGo,
                                    label = "从剪贴板导入",
                                    onClick = onImportClipboard
                                )
                                SheetQuickActionButton(
                                    icon = Icons.Rounded.QrCodeScanner,
                                    label = "扫描二维码导入",
                                    onClick = onOpenQrScan
                                )
                                SheetQuickActionButton(
                                    icon = Icons.Rounded.AddLink,
                                    label = "添加订阅链接",
                                    onClick = onOpenAddSub
                                )
                            }
                        }
                    } else {
                        nodes.forEach { node ->
                            val active = node.id == selectedNodeId
                            LiquidGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 21.dp,
                                accentBorder = if (active) Color(0xE646B5FF) else null,
                                onClick = { onSelectNode(node.id) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(15.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = node.name,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ProtoTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(5.dp))
                                        val pingText = when {
                                            node.latencyMs == null -> "未测速"
                                            node.latencyMs <= 0L -> "超时"
                                            else -> "${node.latencyMs} ms"
                                        }
                                        Text(
                                            text = "${node.badgeText()} · ${node.displayAddressSummary()} · $pingText",
                                            fontSize = 12.sp,
                                            color = Color(0xFF9EAFC3),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(21.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (active) Color(0xFF168BFF) else Color(0xDB091A30)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (active) Color(0xFF78D8FF) else Color(0x9E6BA6E0),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (active) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = "已选中",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                SheetQuickActionButton(
                    icon = Icons.Rounded.Tune,
                    label = "管理全部节点与订阅",
                    onClick = onOpenNodesTab
                )
            }
        }
    }
}

@Composable
private fun SheetQuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x99142840))
            .border(1.dp, Color(0x665B95D1), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF79D1FF),
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = ProtoTextPrimary
        )
    }
}

@Composable
fun LatencyBadge(latencyMs: Long?) {
    val (text, color) = when {
        latencyMs == null -> "未测速" to ProtoTextMuted
        latencyMs <= 0 -> "超时" to ErrorRed
        latencyMs < 200 -> "$latencyMs ms" to ConnectedGreen
        latencyMs < 500 -> "$latencyMs ms" to WarningAmber
        else -> "$latencyMs ms" to ErrorRed
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color
    )
}
