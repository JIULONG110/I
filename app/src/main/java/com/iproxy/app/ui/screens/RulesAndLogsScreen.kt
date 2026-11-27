package com.iproxy.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.iproxy.app.ui.components.LiquidGlassCard
import com.iproxy.app.ui.components.darkFilterChipBorder
import com.iproxy.app.ui.components.darkFilterChipColors
import com.iproxy.app.ui.components.darkOutlinedTextFieldColors
import com.iproxy.app.ui.components.darkTopAppBarColors
import com.iproxy.app.ui.theme.CyanAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 分流规则、分应用代理与实时核心日志页面
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("分流与日志") },
                colors = darkTopAppBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = CyanAccent
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    selectedContentColor = CyanAccent,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = { Text("分流规则") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    selectedContentColor = CyanAccent,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = { Text("运行日志 (${logs.size})") }
                )
            }

            if (selectedTab == 0) {
                RulesConfigTab(
                    settings = settings,
                    isUpdatingGeo = isUpdatingGeo,
                    onUpdateSettings = onUpdateSettings,
                    onSetRoutingMode = onSetRoutingMode,
                    onUpdateGeoDatabases = onUpdateGeoDatabases,
                    onOpenPerAppSelector = onOpenPerAppSelector
                )
            } else {
                LogsConsoleTab(
                    logs = logs,
                    onCopyLogs = onCopyLogs,
                    onClearLogs = onClearLogs
                )
            }
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
    onOpenPerAppSelector: () -> Unit
) {
    var directRules by remember(settings.customDirectRules) { mutableStateOf(settings.customDirectRules) }
    var proxyRules by remember(settings.customProxyRules) { mutableStateOf(settings.customProxyRules) }
    var blockRules by remember(settings.customBlockRules) { mutableStateOf(settings.customBlockRules) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isUpdatingGeo) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // 路由模式卡片
        LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("主路由模式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                RoutingMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetRoutingMode(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.routingMode == mode,
                            onClick = { onSetRoutingMode(mode) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mode.title, fontWeight = FontWeight.Medium)
                            Text(
                                mode.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 广告拦截 & Geo 数据库更新
        LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("拦截常见广告域名", fontWeight = FontWeight.Medium)
                        Text(
                            "基于 geosite:category-ads-all 自动过滤广告请求",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.blockAds,
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(blockAds = enabled) }
                        }
                    )
                }

                OutlinedButton(
                    onClick = onUpdateGeoDatabases,
                    enabled = !isUpdatingGeo,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CloudDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isUpdatingGeo) "正在下载最新规则库..." else "在线更新 GeoIP / GeoSite 规则库")
                }
            }
        }

        // 分应用代理卡片
        LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Apps, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("分应用代理", fontWeight = FontWeight.Bold)
                        Text(
                            "当前: ${settings.perAppMode.title}（已选 ${settings.selectedAppPackages.size} 个应用）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PerAppMode.entries.forEach { mode ->
                        val selected = settings.perAppMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateSettings { it.copy(perAppMode = mode) } },
                            colors = darkFilterChipColors(),
                            border = darkFilterChipBorder(selected),
                            label = {
                                Text(
                                    when (mode) {
                                        PerAppMode.DISABLED -> "关闭"
                                        PerAppMode.BYPASS_SELECTED -> "绕过所选"
                                        PerAppMode.ONLY_PROXY_SELECTED -> "仅代理所选"
                                    }
                                )
                            }
                        )
                    }
                }
                Text(
                    text = "仅在您进入应用列表时读取本机已安装应用名称与包名，用于分应用代理规则，不上传个人数据。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (settings.perAppMode != PerAppMode.DISABLED) {
                    Button(onClick = onOpenPerAppSelector, modifier = Modifier.fillMaxWidth()) {
                        Text("选择应用 (${settings.selectedAppPackages.size})")
                    }
                }
            }
        }

        // 自定义域名/IP规则
        LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("自定义规则（每行一个域名或 IP/CIDR）", fontWeight = FontWeight.Bold)
                val fieldColors = darkOutlinedTextFieldColors()
                OutlinedTextField(
                    value = proxyRules,
                    onValueChange = { proxyRules = it },
                    label = { Text("强制走代理规则 (例如 openai.com, google.com)") },
                    minLines = 2,
                    maxLines = 4,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = directRules,
                    onValueChange = { directRules = it },
                    label = { Text("强制直连规则 (例如 baidu.com, 192.168.0.0/16)") },
                    minLines = 2,
                    maxLines = 4,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = blockRules,
                    onValueChange = { blockRules = it },
                    label = { Text("屏蔽拦截规则") },
                    minLines = 2,
                    maxLines = 4,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
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
                    Text("保存自定义规则")
                }
            }
        }
    }
}

@Composable
private fun LogsConsoleTab(
    logs: List<LogEntry>,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit
) {
    var levelFilter by remember { mutableStateOf("ALL") }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.ROOT) }
    val filteredLogs = remember(logs, levelFilter) {
        if (levelFilter == "ALL") logs else logs.filter { it.level == levelFilter }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL", "INFO", "WARN", "ERROR").forEach { lvl ->
                    val selected = levelFilter == lvl
                    FilterChip(
                        selected = selected,
                        onClick = { levelFilter = lvl },
                        colors = darkFilterChipColors(),
                        border = darkFilterChipBorder(selected),
                        label = { Text(lvl) }
                    )
                }
            }
            Row {
                IconButton(onClick = onCopyLogs) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "复制日志")
                }
                IconButton(onClick = onClearLogs) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "清空日志")
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .background(Color(0xFF07101F), RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            if (filteredLogs.isEmpty()) {
                Text(
                    text = "暂无运行日志，连接节点后将在此实时输出诊断信息...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
 * 分应用代理选择页
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("选择分应用代理 (${selectedPackages.size})") },
                colors = darkTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索应用名称或包名...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    colors = darkOutlinedTextFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = showSystemApps,
                    onClick = { showSystemApps = !showSystemApps },
                    colors = darkFilterChipColors(),
                    border = darkFilterChipBorder(showSystemApps),
                    label = { Text("系统应用") }
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = app.packageName in selectedPackages
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTogglePackage(app.packageName) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onTogglePackage(app.packageName) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, fontWeight = FontWeight.Medium)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
