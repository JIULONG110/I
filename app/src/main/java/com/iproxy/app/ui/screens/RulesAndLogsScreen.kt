package com.iproxy.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iproxy.app.data.model.AppSettings
import com.iproxy.app.data.model.LogEntry
import com.iproxy.app.data.model.PerAppMode
import com.iproxy.app.data.model.RoutingMode
import com.iproxy.app.data.repository.InstalledAppInfo
import com.iproxy.app.ui.components.BlueConstellationParticlesCanvas
import com.iproxy.app.ui.components.GlassActionButton
import com.iproxy.app.ui.components.GlassCheckbox
import com.iproxy.app.ui.components.GlassEmptyStateCard
import com.iproxy.app.ui.components.GlassLoadingBar
import com.iproxy.app.ui.components.GlassLoadingIndicator
import com.iproxy.app.ui.components.GlassSecondaryButton
import com.iproxy.app.ui.components.GlassSelectableChip
import com.iproxy.app.ui.components.GlassSmallIconButton
import com.iproxy.app.ui.components.GlassTextField
import com.iproxy.app.ui.components.LiquidGlassCard
import com.iproxy.app.ui.components.ProtoTextMuted
import com.iproxy.app.ui.components.ProtoTextPrimary
import com.iproxy.app.ui.components.ProtoTextSecondary
import com.iproxy.app.ui.components.PrototypeRoutingModeCard
import com.iproxy.app.ui.components.PrototypeSwitch
import com.iproxy.app.ui.components.drawGlassCapsule3D
import com.iproxy.app.ui.components.rememberReduceMotionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 分流规则、分应用代理与实时核心日志页面：所有选项、开关、按钮、输入与日志容器均具 3D 蓝色实体玻璃与星链流光粒子。
 */
@Composable
fun RulesAndLogsScreen(
    settings: AppSettings,
    logs: List<LogEntry>,
    isUpdatingGeo: Boolean,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onSetRoutingMode: (RoutingMode) -> Unit,
    onUpdateGeoDatabases: () -> Unit,
    onOpenPerAppSelector: () -> Unit,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val reduceMotion = rememberReduceMotionState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "智能分流",
            fontSize = 24.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.Bold,
            color = ProtoTextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "选择网络流量的转发方式与过滤规则",
            fontSize = 13.sp,
            color = ProtoTextMuted
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3D 玻璃分栏切换（选中与未选中均保持流动蓝色星链粒子）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))
                .drawBehind {
                    drawGlassCapsule3D(cornerRadiusDp = 17.dp, active = false, innerBevelInsetDp = 2.dp)
                }
                .padding(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("分流规则", "运行日志 (${logs.size})").forEachIndexed { index, title ->
                    val active = selectedTab == index
                    val pillShape = RoundedCornerShape(13.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(pillShape)
                            .drawBehind {
                                drawGlassCapsule3D(
                                    cornerRadiusDp = 13.dp,
                                    active = active,
                                    innerBevelInsetDp = 2.dp
                                )
                            }
                            .clickable { selectedTab = index },
                        contentAlignment = Alignment.Center
                    ) {
                        BlueConstellationParticlesCanvas(
                            reduceMotion = reduceMotion,
                            densityMultiplier = 0.68f,
                            timeOffsetSec = index * 1.4f,
                            intensity = if (active) 1.08f else 0.72f,
                            modifier = Modifier.matchParentSize()
                        )
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) Color.White else Color(0xFFD2C7FA),
                            maxLines = 1,
                            modifier = Modifier.padding(vertical = 9.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) {
            RulesConfigTab(
                settings = settings,
                isUpdatingGeo = isUpdatingGeo,
                onUpdateSettings = onUpdateSettings,
                onSetRoutingMode = onSetRoutingMode,
                onUpdateGeoDatabases = onUpdateGeoDatabases,
                onOpenPerAppSelector = onOpenPerAppSelector,
                modifier = Modifier.weight(1f)
            )
        } else {
            LogsConsoleTab(
                logs = logs,
                onCopyLogs = onCopyLogs,
                onClearLogs = onClearLogs,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RulesConfigTab(
    settings: AppSettings,
    isUpdatingGeo: Boolean,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onSetRoutingMode: (RoutingMode) -> Unit,
    onUpdateGeoDatabases: () -> Unit,
    onOpenPerAppSelector: () -> Unit,
    modifier: Modifier = Modifier
) {
    var directRules by remember(settings.customDirectRules) { mutableStateOf(settings.customDirectRules) }
    var proxyRules by remember(settings.customProxyRules) { mutableStateOf(settings.customProxyRules) }
    var blockRules by remember(settings.customBlockRules) { mutableStateOf(settings.customBlockRules) }

    val routePreview = when (settings.routingMode) {
        RoutingMode.RULE_BYPASS_CN -> "中国大陆域名与 IP 将直连，海外与未命中流量通过当前节点代理。"
        RoutingMode.GLOBAL_PROXY -> "除局域网外的流量将通过当前节点转发。"
        RoutingMode.DIRECT -> "所有流量均直连访问，不经过代理节点。"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isUpdatingGeo) {
            GlassLoadingBar(modifier = Modifier.fillMaxWidth())
        }

        // 与首页一致的粒子分流卡（三枚按钮均流光）
        PrototypeRoutingModeCard(
            routingMode = settings.routingMode,
            onSelectMode = onSetRoutingMode
        )

        // 当前规则预览卡
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            particleCount = 20,
            seedOffsetSec = 0.7f
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "当前规则预览",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProtoTextPrimary
                )
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = routePreview,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = ProtoTextSecondary
                )
            }
        }

        // 广告拦截 & Geo 数据库更新
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            particleCount = 22,
            seedOffsetSec = 1.4f
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpdateSettings { it.copy(blockAds = !settings.blockAds) } },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Block, null, tint = Color(0xFF4BE8FF))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("拦截常见广告域名", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ProtoTextPrimary)
                        Text(
                            "基于 geosite:category-ads-all 自动过滤广告请求",
                            fontSize = 11.sp,
                            color = ProtoTextMuted
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    PrototypeSwitch(
                        checked = settings.blockAds,
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(blockAds = enabled) }
                        }
                    )
                }

                GlassSecondaryButton(
                    onClick = onUpdateGeoDatabases,
                    enabled = !isUpdatingGeo,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CloudDownload, null, tint = Color(0xFFD7C8FF), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isUpdatingGeo) "正在下载最新规则库..." else "在线更新 GeoIP / GeoSite 规则库",
                        fontWeight = FontWeight.SemiBold,
                        color = ProtoTextPrimary
                    )
                }
            }
        }

        // 分应用代理卡片（分应用模式按钮均具流动粒子）
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            isActive = settings.perAppMode != PerAppMode.DISABLED,
            seedOffsetSec = 2.1f
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Apps, null, tint = Color(0xFF4BE8FF))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("分应用代理", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ProtoTextPrimary)
                        Text(
                            "当前: ${settings.perAppMode.title}（已选 ${settings.selectedAppPackages.size} 个应用）",
                            fontSize = 11.sp,
                            color = ProtoTextMuted
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    PerAppMode.entries.forEachIndexed { idx, mode ->
                        val active = settings.perAppMode == mode
                        val label = when (mode) {
                            PerAppMode.DISABLED -> "关闭"
                            PerAppMode.BYPASS_SELECTED -> "绕过所选"
                            PerAppMode.ONLY_PROXY_SELECTED -> "仅代理所选"
                        }
                        GlassSelectableChip(
                            label = label,
                            selected = active,
                            seedOffsetSec = idx * 0.9f,
                            horizontalPadding = 6.dp,
                            verticalPadding = 9.dp,
                            onClick = { onUpdateSettings { it.copy(perAppMode = mode) } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (settings.perAppMode != PerAppMode.DISABLED) {
                    GlassActionButton(
                        onClick = onOpenPerAppSelector,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("选择应用 (${settings.selectedAppPackages.size})", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 自定义域名/IP 规则
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            particleCount = 26,
            seedOffsetSec = 2.7f
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("自定义规则（每行一个域名或 IP/CIDR）", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ProtoTextPrimary)
                GlassTextField(
                    value = proxyRules,
                    onValueChange = { proxyRules = it },
                    label = "强制走代理规则 (例如 openai.com, google.com)",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                GlassTextField(
                    value = directRules,
                    onValueChange = { directRules = it },
                    label = "强制直连规则 (例如 baidu.com, 192.168.0.0/16)",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                GlassTextField(
                    value = blockRules,
                    onValueChange = { blockRules = it },
                    label = "屏蔽拦截规则",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                GlassActionButton(
                    onClick = {
                        onUpdateSettings {
                            it.copy(
                                customProxyRules = proxyRules,
                                customDirectRules = directRules,
                                customBlockRules = blockRules
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存自定义规则", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding())
        Spacer(modifier = Modifier.height(112.dp))
    }
}

@Composable
private fun LogsConsoleTab(
    logs: List<LogEntry>,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var levelFilter by remember { mutableStateOf("ALL") }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.ROOT) }
    val filteredLogs = remember(logs, levelFilter) {
        if (levelFilter == "ALL") logs else logs.filter { it.level == levelFilter }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 104.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL", "INFO", "WARN", "ERROR").forEachIndexed { idx, lvl ->
                    GlassSelectableChip(
                        label = lvl,
                        selected = levelFilter == lvl,
                        cornerRadius = 10.dp,
                        seedOffsetSec = idx * 0.7f,
                        horizontalPadding = 10.dp,
                        verticalPadding = 6.dp,
                        onClick = { levelFilter = lvl }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassSmallIconButton(
                    icon = Icons.Rounded.ContentCopy,
                    contentDescription = "复制日志",
                    onClick = onCopyLogs
                )
                GlassSmallIconButton(
                    icon = Icons.Rounded.DeleteSweep,
                    contentDescription = "清空日志",
                    onClick = onClearLogs
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                GlassEmptyStateCard(
                    title = "暂无运行日志",
                    subtitle = "连接节点后将在此实时输出 Xray 内核与 TUN 隧道诊断信息。"
                )
            }
        } else {
            LiquidGlassCard(
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 20.dp,
                particleCount = 26
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredLogs.reversed()) { entry ->
                        val color = when (entry.level) {
                            "ERROR" -> Color(0xFFF87171)
                            "WARN" -> Color(0xFFFBBF24)
                            else -> Color(0xFF93C5FD)
                        }
                        Text(
                            text = "${timeFormatter.format(Date(entry.timestampMs))} [${entry.level}/${entry.tag}] ${entry.message}",
                            color = color,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 分应用代理选择页：完全剔除灰色 Material 控件，全量接入 3D 玻璃顶栏、搜索栏、系统应用开关芯片、玻璃复选框与玻璃加载动画
 */
@Composable
fun PerAppSelectorScreen(
    apps: List<InstalledAppInfo>,
    isLoading: Boolean,
    selectedPackages: Set<String>,
    onLoadApps: () -> Unit,
    onTogglePackage: (String) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotionState()

    LaunchedEffect(Unit) {
        onLoadApps()
    }

    val filtered = remember(apps, query, showSystemApps) {
        apps.filter { app ->
            (showSystemApps || !app.isSystemApp) &&
                (query.isBlank() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(18.dp))
                .drawBehind {
                    drawGlassCapsule3D(cornerRadiusDp = 18.dp, active = false, elevated = true)
                }
        ) {
            BlueConstellationParticlesCanvas(
                particleCount = 20,
                reduceMotion = reduceMotion,
                referenceWidthDp = 360f,
                linkMaxDistDp = 40f,
                intensity = 0.72f,
                modifier = Modifier.matchParentSize()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassSmallIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    sizeDp = 36.dp,
                    cornerRadius = 12.dp,
                    onClick = onBack
                )
                Text(
                    text = "选择分应用代理 (${selectedPackages.size})",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProtoTextPrimary
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索应用名称或包名...",
                leadingIcon = Icons.Rounded.Search,
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            GlassSelectableChip(
                label = "系统应用",
                selected = showSystemApps,
                onClick = { showSystemApps = !showSystemApps }
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassLoadingIndicator(label = "正在读取已安装应用列表...")
            }
        } else if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassEmptyStateCard(
                    title = "未找到匹配应用",
                    subtitle = "可修改搜索词或切换上方「系统应用」显示开关。"
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(filtered, key = { _, app -> app.packageName }) { idx, app ->
                    val checked = app.packageName in selectedPackages
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        isActive = checked,
                        accentBorder = if (checked) Color(0xCC9F5EFF) else null,
                        seedOffsetSec = (idx % 12) * 0.4f,
                        onClick = { onTogglePackage(app.packageName) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassCheckbox(
                                checked = checked,
                                onCheckedChange = { onTogglePackage(app.packageName) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, fontWeight = FontWeight.Medium, color = ProtoTextPrimary)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ProtoTextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
