package com.iproxy.app.ui.screens

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.iproxy.app.ui.components.GlassDivider
import com.iproxy.app.ui.components.GlassEmptyStateCard
import com.iproxy.app.ui.components.GlassSecondaryButton
import com.iproxy.app.ui.components.GlassSmallIconButton
import com.iproxy.app.ui.components.LiquidGlassCard
import com.iproxy.app.ui.components.ProtoAccentSky
import com.iproxy.app.ui.components.ProtoDividerBlue
import com.iproxy.app.ui.components.ProtoTextPrimary
import com.iproxy.app.ui.components.ProtoTextSecondary
import com.iproxy.app.ui.components.PrototypeRoutingModeCard
import com.iproxy.app.ui.components.ProvideParticleAnimationClock
import com.iproxy.app.ui.components.drawGlassCapsule3D
import com.iproxy.app.ui.components.rememberReduceMotionState
import com.iproxy.app.ui.theme.ConnectedGreen
import com.iproxy.app.ui.theme.ErrorRed
import com.iproxy.app.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * 首页连接仪表盘：全矢量 3D 实体玻璃反应舱试管（无静态图、开关两态一致高亮且内部不置图标文字）+ 实时网速 + 当前节点 + 分流卡
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
            Spacer(modifier = Modifier.height(if (compactHeight) 14.dp else 30.dp))

            // 中央纯运行时渲染 3D 实体玻璃反应舱试管 + 84 颗高速蓝色流动星链粒子（不显示文字与图标，两态一致透亮）
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
                // 实时上传 / 下载流量与真实连接态指示卡
                TrafficSummaryCard(
                    runtimeState = runtimeState,
                    elapsedMs = elapsedMs,
                    compactHeight = compactHeight,
                    onClick = {
                        val tip = when (runtimeState.status) {
                            VpnStatus.CONNECTED -> "安全通道运行中 · 已持续 ${FormatUtils.formatDuration(elapsedMs)}"
                            VpnStatus.CONNECTING -> "正在建立安全加密通道..."
                            VpnStatus.DISCONNECTING -> "正在断开安全加密通道..."
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

                // 智能分流模式卡（选中与未选中均保持立体玻璃与快速流动蓝色星链粒子，清晰辨识当前模式）
                PrototypeRoutingModeCard(
                    routingMode = settings.routingMode,
                    onSelectMode = onChangeRoutingMode
                )

                if (!runtimeState.lastError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        accentBorder = ErrorRed,
                        particleCount = 16
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

/**
 * 纯 Compose Canvas 运行时构建的 3D 圆角玻璃实体试管反应舱：
 * 全程不依赖静态贴图/GIF，内部持续流动 84 颗多层次蓝青粒子与星链连线；两态质感亮度一致，无文字或图标遮挡。
 */
@Composable
private fun ReactorHeroCylinder(
    status: VpnStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotionState()
    val stateLabel = when (status) {
        VpnStatus.CONNECTED -> "已连接"
        VpnStatus.CONNECTING -> "连接中"
        VpnStatus.DISCONNECTING -> "正在断开"
        VpnStatus.ERROR -> "异常"
        VpnStatus.DISCONNECTED -> "未连接"
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val heroW = maxWidth
        val heroH = maxHeight
        val chamberWidth = min(heroW.value * 0.64f, heroH.value * 0.88f).coerceAtLeast(180f).dp
        val chamberHeight = (heroH * 0.86f).coerceAtLeast(184.dp)

        // 1. 底部扩散环境深海钴蓝光池
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseGlowCenter = Offset(size.width * 0.5f, size.height * 0.84f)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x541B87FF),
                        Color(0x280C4AA8),
                        Color.Transparent
                    ),
                    center = baseGlowCenter,
                    radius = chamberWidth.toPx() * 0.82f
                ),
                topLeft = Offset(
                    baseGlowCenter.x - chamberWidth.toPx() * 0.78f,
                    baseGlowCenter.y - chamberHeight.toPx() * 0.20f
                ),
                size = Size(chamberWidth.toPx() * 1.56f, chamberHeight.toPx() * 0.40f)
            )
        }

        // 2. 实体 3D 蓝色玻璃试管主体 + 84 颗高速流动星链粒子
        Box(
            modifier = Modifier
                .size(width = chamberWidth, height = chamberHeight)
                .clip(RoundedCornerShape(34.dp))
        ) {
            // 玻璃底层立体体光与内腔景深
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val corner = CornerRadius(34.dp.toPx(), 34.dp.toPx())

                // 主体深海玻璃圆柱体渐变
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0.0f to Color(0xF010253D),
                        0.28f to Color(0xF50A192E),
                        0.68f to Color(0xF20D376E),
                        0.92f to Color(0xF5115BBF),
                        1.0f to Color(0xF50A2247)
                    ),
                    cornerRadius = corner
                )

                // 中下部强钴蓝焦散折射光池
                val floorCenter = Offset(w * 0.5f, h * 0.82f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x9C28A8FF), Color(0x471565D8), Color.Transparent),
                        center = floorCenter,
                        radius = w * 0.72f
                    ),
                    radius = w * 0.72f,
                    center = floorCenter
                )

                // 左右立体柱状玻璃反光肋条
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        0.0f to Color(0x47BCEAFF),
                        0.07f to Color(0x1E65B9FF),
                        0.15f to Color.Transparent,
                        0.85f to Color.Transparent,
                        0.93f to Color(0x1E65B9FF),
                        1.0f to Color(0x42B5E8FF)
                    ),
                    cornerRadius = corner
                )
            }

            // 内部 84 颗高速流动蓝色星链粒子（两态一致通透流动）
            BlueConstellationParticlesCanvas(
                particleCount = 84,
                reduceMotion = reduceMotion,
                referenceWidthDp = 340f,
                linkMaxDistDp = 52f,
                intensity = 1.15f,
                radiusScale = 1.05f,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 5.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(28.dp))
            )

            // 前景玻璃立体盖环、内嵌管壁倒角与表层反光高光
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val outerR = 34.dp.toPx()
                val corner = CornerRadius(outerR, outerR)
                val ringH = (h * 0.14f).coerceAtLeast(22.dp.toPx())

                // 顶部实体立体玻璃上环口
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0.0f to Color(0x88AEDFFF),
                        0.45f to Color(0x3D4C8CD4),
                        1.0f to Color(0x0D194275)
                    ),
                    size = Size(w, ringH),
                    cornerRadius = CornerRadius(outerR, outerR)
                )
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0xB8CFEFFF), Color.Transparent)
                    ),
                    start = Offset(outerR * 0.55f, ringH * 0.92f),
                    end = Offset(w - outerR * 0.55f, ringH * 0.92f),
                    strokeWidth = 1.1.dp.toPx()
                )

                // 底部实体玻璃底座环口
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0.0f to Color(0x142A6AB8),
                        0.55f to Color(0x6649A7FF),
                        1.0f to Color(0x889FE4FF)
                    ),
                    topLeft = Offset(0f, h - ringH),
                    size = Size(w, ringH),
                    cornerRadius = CornerRadius(outerR, outerR)
                )

                // 内嵌 4dp 实体玻璃内壁描边
                val bevelInset = 4.dp.toPx()
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0.0f to Color(0x9E9EE4FF),
                        0.5f to Color(0x3858A8EB),
                        1.0f to Color(0x807CD8FF)
                    ),
                    topLeft = Offset(bevelInset, bevelInset),
                    size = Size(w - bevelInset * 2f, h - bevelInset * 2f),
                    cornerRadius = CornerRadius(outerR - bevelInset * 0.6f, outerR - bevelInset * 0.6f),
                    style = Stroke(width = 1.dp.toPx())
                )

                // 外层立体玻璃主外框
                drawRoundRect(
                    brush = Brush.linearGradient(
                        0.0f to Color(0xE89CE4FF),
                        0.5f to Color(0x9452AEFF),
                        1.0f to Color(0xC976D1FF)
                    ),
                    cornerRadius = corner,
                    style = Stroke(width = 1.6.dp.toPx())
                )
            }
        }

        // 3. 透明无障碍触控热区（点击真实触发连接/断开，不覆盖任何视觉或图标文字）
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(width = chamberWidth, height = chamberHeight)
                .clip(RoundedCornerShape(34.dp))
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
    elapsedMs: Long,
    compactHeight: Boolean,
    onClick: () -> Unit
) {
    val isConnected = runtimeState.status == VpnStatus.CONNECTED
    val isTransitioning =
        runtimeState.status == VpnStatus.CONNECTING || runtimeState.status == VpnStatus.DISCONNECTING
    val statusDotColor = when (runtimeState.status) {
        VpnStatus.CONNECTED -> Color(0xFF47E5FF)
        VpnStatus.CONNECTING, VpnStatus.DISCONNECTING -> WarningAmber
        VpnStatus.ERROR -> ErrorRed
        VpnStatus.DISCONNECTED -> Color(0xFF5D7694)
    }
    val statusHeadline = when (runtimeState.status) {
        VpnStatus.CONNECTED -> "通道已开启 · ${FormatUtils.formatDuration(elapsedMs)}"
        VpnStatus.CONNECTING -> "正在建立通道..."
        VpnStatus.DISCONNECTING -> "正在断开通道..."
        VpnStatus.ERROR -> "通道连接异常"
        VpnStatus.DISCONNECTED -> "待机中 · 未连接"
    }

    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compactHeight) 86.dp else 94.dp),
        cornerRadius = 24.dp,
        isActive = isConnected || isTransitioning,
        particleCount = 26,
        seedOffsetSec = 0.3f,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(statusDotColor)
                )
                Text(
                    text = statusHeadline,
                    fontSize = 11.sp,
                    fontWeight = if (isConnected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isConnected) Color(0xFFB5ECFF) else Color(0xFF8CA4BE),
                    maxLines = 1
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        .height(48.dp)
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
        particleCount = 26,
        seedOffsetSec = 1.4f,
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
                            .drawBehind {
                                drawGlassCapsule3D(
                                    cornerRadiusDp = 10.dp,
                                    active = false,
                                    innerBevelInsetDp = 1.5.dp
                                )
                            }
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = selectedNode?.badgeText() ?: "导入配置",
                            fontSize = 11.sp,
                            color = Color(0xFFAEE2FF),
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
    val reduceMotion = rememberReduceMotionState()
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

    Box(
        modifier = Modifier
            .widthIn(min = if (narrowWidth) 82.dp else 94.dp)
            .height(42.dp)
            .clip(shape)
            .drawBehind {
                drawGlassCapsule3D(
                    cornerRadiusDp = 21.dp,
                    active = isLoading,
                    elevated = true,
                    accentBorder = Color(0xD165ACF7),
                    innerBevelInsetDp = 2.dp
                )
            }
            .clickable(enabled = !isLoading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BlueConstellationParticlesCanvas(
            particleCount = 14,
            reduceMotion = reduceMotion,
            referenceWidthDp = 190f,
            linkMaxDistDp = 34f,
            timeOffsetSec = 2.2f,
            intensity = if (isLoading) 1.15f else 0.85f,
            radiusScale = 0.85f,
            modifier = Modifier.matchParentSize()
        )
        Row(
            modifier = Modifier.padding(horizontal = if (narrowWidth) 9.dp else 13.dp),
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
                        size = Size(14f * sx, 14f * sy),
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
        ProvideParticleAnimationClock {
            val reduceMotion = rememberReduceMotionState()
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
                Box(
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(0.76f)
                        .clip(sheetShape)
                        .drawBehind {
                            drawGlassCapsule3D(
                                cornerRadiusDp = 28.dp,
                                active = false,
                                elevated = true,
                                accentBorder = Color(0x9E64ADE4),
                                innerBevelInsetDp = 3.dp
                            )
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    BlueConstellationParticlesCanvas(
                        particleCount = 54,
                        reduceMotion = reduceMotion,
                        referenceWidthDp = 410f,
                        linkMaxDistDp = 50f,
                        intensity = 0.65f,
                        modifier = Modifier.matchParentSize()
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(width = 40.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(Color(0xA67CC8FF))
                        )
                        Spacer(modifier = Modifier.height(14.dp))
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
                            GlassSmallIconButton(
                                icon = Icons.Rounded.Close,
                                contentDescription = "关闭",
                                sizeDp = 34.dp,
                                cornerRadius = 17.dp,
                                onClick = onDismiss
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        GlassDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (nodes.isEmpty()) {
                                GlassEmptyStateCard(
                                    title = "暂无可用节点",
                                    subtitle = "您可以从剪贴板导入分享链接、扫描二维码或添加订阅链接。"
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        GlassSecondaryButton(
                                            onClick = onImportClipboard,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Rounded.ContentPasteGo, null, tint = Color(0xFF79D1FF), modifier = Modifier.size(17.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("从剪贴板导入")
                                        }
                                        GlassSecondaryButton(
                                            onClick = onOpenQrScan,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Rounded.QrCodeScanner, null, tint = Color(0xFF79D1FF), modifier = Modifier.size(17.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("扫描二维码导入")
                                        }
                                        GlassSecondaryButton(
                                            onClick = onOpenAddSub,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Rounded.AddLink, null, tint = Color(0xFF79D1FF), modifier = Modifier.size(17.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("添加订阅链接")
                                        }
                                    }
                                }
                            } else {
                                nodes.forEachIndexed { idx, node ->
                                    val active = node.id == selectedNodeId
                                    LiquidGlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        cornerRadius = 21.dp,
                                        isActive = active,
                                        accentBorder = if (active) Color(0xE646B5FF) else null,
                                        particleCount = 20,
                                        seedOffsetSec = idx * 0.6f,
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
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .drawBehind {
                                                        drawGlassCapsule3D(
                                                            cornerRadiusDp = 11.dp,
                                                            active = active,
                                                            innerBevelInsetDp = 1.5.dp
                                                        )
                                                    },
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
                        GlassSecondaryButton(
                            onClick = onOpenNodesTab,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, tint = Color(0xFF79D1FF), modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("管理全部节点与订阅", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LatencyBadge(latencyMs: Long?) {
    val (text, color) = when {
        latencyMs == null -> "未测速" to Color(0xFF9FB2C9)
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
