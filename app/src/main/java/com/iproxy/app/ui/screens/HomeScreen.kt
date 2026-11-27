package com.iproxy.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentPasteGo
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iproxy.app.core.util.FormatUtils
import com.iproxy.app.data.model.AppSettings
import com.iproxy.app.data.model.RoutingMode
import com.iproxy.app.data.model.ServerNode
import com.iproxy.app.data.model.VpnRuntimeState
import com.iproxy.app.data.model.VpnStatus
import com.iproxy.app.ui.theme.BrandBlue
import com.iproxy.app.ui.theme.ConnectedGreen
import com.iproxy.app.ui.theme.CyanAccent
import com.iproxy.app.ui.theme.ErrorRed
import com.iproxy.app.ui.theme.WarningAmber
import kotlinx.coroutines.delay

/**
 * 首页连接仪表盘：大号 "I" 按钮、实时速率、节点摘要、分流切换与新手引导
 */
@Composable
fun HomeScreen(
    runtimeState: VpnRuntimeState,
    selectedNode: ServerNode?,
    nodeCount: Int,
    settings: AppSettings,
    isTestingLatency: Boolean,
    onToggleConnect: () -> Unit,
    onSelectNodeClick: () -> Unit,
    onTestCurrentNode: (String) -> Unit,
    onChangeRoutingMode: (RoutingMode) -> Unit,
    onImportClipboard: () -> Unit,
    onOpenQrScan: () -> Unit,
    onOpenAddSub: () -> Unit
) {
    val isConnected = runtimeState.status == VpnStatus.CONNECTED
    val isConnecting = runtimeState.status == VpnStatus.CONNECTING

    // 实时计时器
    var elapsedMs by remember { mutableLongStateOf(0L) }
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

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isConnected) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val ringColor by animateColorAsState(
        targetValue = when (runtimeState.status) {
            VpnStatus.CONNECTED -> ConnectedGreen
            VpnStatus.CONNECTING -> WarningAmber
            VpnStatus.ERROR -> ErrorRed
            else -> MaterialTheme.colorScheme.primary
        },
        label = "ringColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部品牌抬头
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "I",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (settings.proxyOnlyMode) "本地代理模式" else "安全加密 VPN 通道",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = when (runtimeState.status) {
                            VpnStatus.CONNECTED -> "已连接 ${FormatUtils.formatDuration(elapsedMs)}"
                            VpnStatus.CONNECTING -> "连接中..."
                            VpnStatus.ERROR -> "连接异常"
                            else -> "未连接"
                        }
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = ringColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 超大号「I」核心连接按钮
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(210.dp)
        ) {
            // 外圈呼吸光环
            Box(
                modifier = Modifier
                    .size(196.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(ringColor.copy(alpha = if (isConnected) 0.16f else 0.08f))
            )

            Surface(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !isConnecting) { onToggleConnect() },
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(3.dp, ringColor),
                shadowElevation = if (isConnected) 12.dp else 4.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = if (isConnected) {
                                    listOf(Color(0xFF065F46), ConnectedGreen)
                                } else {
                                    listOf(Color(0xFF0F294A), BrandBlue, CyanAccent.copy(alpha = 0.7f))
                                }
                            )
                        )
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(48.dp)
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "I",
                                fontSize = 54.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = if (isConnected) "点击断开" else "点击连接",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }

        if (!runtimeState.lastError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "错误: ${runtimeState.lastError}",
                color = ErrorRed,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 实时上下行网速与总流量卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TrafficStatColumn(
                    icon = Icons.Rounded.ArrowUpward,
                    tint = BrandBlue,
                    title = "上传速率",
                    speed = FormatUtils.formatSpeed(runtimeState.uplinkSpeedBps),
                    total = "总计 ${FormatUtils.formatBytes(runtimeState.totalUplinkBytes)}"
                )
                TrafficStatColumn(
                    icon = Icons.Rounded.ArrowDownward,
                    tint = ConnectedGreen,
                    title = "下载速率",
                    speed = FormatUtils.formatSpeed(runtimeState.downlinkSpeedBps),
                    total = "总计 ${FormatUtils.formatBytes(runtimeState.totalDownlinkBytes)}"
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 当前节点卡片或零基础引导卡片
        if (selectedNode != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectNodeClick() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "当前节点（共 $nodeCount 个）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = selectedNode.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = selectedNode.badgeText(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            LatencyBadge(latencyMs = selectedNode.latencyMs)
                        }
                    }
                    OutlinedButton(
                        onClick = { onTestCurrentNode(selectedNode.id) },
                        enabled = !isTestingLatency
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = "测速",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isTestingLatency) "测速中" else "测速")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "切换节点",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // 零基础首次使用三步引导卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "新手快速开始（3 秒上手）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "您还没有任何节点。只需复制一条订阅链接或节点分享链接，点击下方任意导入方式，然后点击上方「I」即可开始上网。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onImportClipboard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.ContentPasteGo, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("从剪贴板一键导入节点 / 订阅")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenQrScan,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("扫码导入")
                        }
                        OutlinedButton(
                            onClick = onOpenAddSub,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("添加订阅")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 路由模式快速切换
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "智能分流模式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RoutingMode.entries.forEach { mode ->
                        val selected = settings.routingMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onChangeRoutingMode(mode) },
                            label = {
                                Text(
                                    text = when (mode) {
                                        RoutingMode.RULE_BYPASS_CN -> "绕过大陆"
                                        RoutingMode.GLOBAL_PROXY -> "全局代理"
                                        RoutingMode.DIRECT -> "直连"
                                    },
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = settings.routingMode.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TrafficStatColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    speed: String,
    total: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(speed, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(total, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LatencyBadge(latencyMs: Long?) {
    val (text, color) = when {
        latencyMs == null -> "未测速" to MaterialTheme.colorScheme.onSurfaceVariant
        latencyMs <= 0 -> "超时" to ErrorRed
        latencyMs < 200 -> "${latencyMs} ms" to ConnectedGreen
        latencyMs < 500 -> "${latencyMs} ms" to WarningAmber
        else -> "${latencyMs} ms" to ErrorRed
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color
    )
}
