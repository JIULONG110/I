package com.iproxy.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iproxy.app.BuildConfig
import com.iproxy.app.data.model.AppSettings
import com.iproxy.app.ui.components.BlackBrandLogo
import com.iproxy.app.ui.components.IProxyAlertDialog
import com.iproxy.app.ui.components.LiquidGlassCard
import com.iproxy.app.ui.components.darkOutlinedTextFieldColors
import com.iproxy.app.ui.components.darkTopAppBarColors
import kotlinx.coroutines.launch
import libv2ray.Libv2ray

/**
 * 高级网络设置、外观主题、版本检查与配置备份页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    isCheckingUpdate: Boolean,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onCheckUpdate: () -> Unit,
    onExportBackup: suspend () -> String,
    onImportBackup: (String) -> Unit,
    onCopyText: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var remoteDns by remember(settings.remoteDns) { mutableStateOf(settings.remoteDns) }
    var directDns by remember(settings.directDns) { mutableStateOf(settings.directDns) }
    var socksPortStr by remember(settings.socksPort) { mutableStateOf(settings.socksPort.toString()) }
    var httpPortStr by remember(settings.httpPort) { mutableStateOf(settings.httpPort.toString()) }
    var mtuStr by remember(settings.vpnMtu) { mutableStateOf(settings.vpnMtu.toString()) }
    var speedUrl by remember(settings.speedTestUrl) { mutableStateOf(settings.speedTestUrl) }
    var showImportBackupDialog by remember { mutableStateOf(false) }
    val xrayVersion = remember { runCatching { Libv2ray.checkVersionX() }.getOrDefault("Xray-core") }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = darkTopAppBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. 核心网络开关
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("网络与协议增强", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    SettingToggleRow(
                        title = "局域网共享代理",
                        subtitle = "监听 0.0.0.0:${settings.socksPort}/${settings.httpPort}，允许同 Wi-Fi 设备共享连接",
                        checked = settings.allowLanShare,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(allowLanShare = v) } }
                    )
                    SettingToggleRow(
                        title = "仅本地代理模式（不开 VPN）",
                        subtitle = "仅开启本地 SOCKS5/HTTP 端口，适合配合其他系统代理使用",
                        checked = settings.proxyOnlyMode,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(proxyOnlyMode = v) } }
                    )
                    SettingToggleRow(
                        title = "启用 IPv6 路由",
                        subtitle = "若当地网络和节点均支持 IPv6 可开启",
                        checked = settings.enableIpv6,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(enableIpv6 = v) } }
                    )
                    SettingToggleRow(
                        title = "FakeDNS 加速",
                        subtitle = "减少 DNS 查询延迟，提升网页首包速度",
                        checked = settings.enableFakeDns,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(enableFakeDns = v) } }
                    )
                    SettingToggleRow(
                        title = "Mux 多路复用",
                        subtitle = "合并并发 TCP 连接（不适用于 XTLS Vision 与 XHTTP）",
                        checked = settings.enableMux,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(enableMux = v) } }
                    )
                    SettingToggleRow(
                        title = "TLS Fragment 分片防阻断",
                        subtitle = "将 TLS 握手分片发送以降低特定网络干扰",
                        checked = settings.enableFragment,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(enableFragment = v) } }
                    )
                }
            }

            // 2. DNS 与端口参数
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("DNS 与本地端口", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val fieldColors = darkOutlinedTextFieldColors()

                    OutlinedTextField(
                        value = remoteDns,
                        onValueChange = { remoteDns = it },
                        label = { Text("境外代理 DNS (例如 1.1.1.1 或 8.8.8.8)") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = directDns,
                        onValueChange = { directDns = it },
                        label = { Text("国内直连 DNS (例如 223.5.5.5)") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = socksPortStr,
                            onValueChange = { socksPortStr = it.filter { c -> c.isDigit() } },
                            label = { Text("SOCKS5 端口") },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = httpPortStr,
                            onValueChange = { httpPortStr = it.filter { c -> c.isDigit() } },
                            label = { Text("HTTP 端口") },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = mtuStr,
                            onValueChange = { mtuStr = it.filter { c -> c.isDigit() } },
                            label = { Text("MTU") },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = speedUrl,
                        onValueChange = { speedUrl = it },
                        label = { Text("真连接测速目标 URL") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onUpdateSettings {
                                it.copy(
                                    remoteDns = remoteDns.trim().ifBlank { "1.1.1.1" },
                                    directDns = directDns.trim().ifBlank { "223.5.5.5" },
                                    socksPort = socksPortStr.toIntOrNull()?.takeIn(1024, 65535) ?: 10808,
                                    httpPort = httpPortStr.toIntOrNull()?.takeIn(1024, 65535) ?: 10809,
                                    vpnMtu = mtuStr.toIntOrNull()?.takeIn(1200, 9000) ?: 1500,
                                    speedTestUrl = speedUrl.trim().ifBlank { "https://www.gstatic.com/generate_204" }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存网络参数")
                    }
                }
            }

            // 4. 配置备份与迁移
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("配置备份与迁移", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val json = onExportBackup()
                                    onCopyText(json, "已导出全部节点与设置备份 JSON 到剪贴板")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导出备份")
                        }
                        OutlinedButton(
                            onClick = { showImportBackupDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("恢复备份")
                        }
                    }
                }
            }

            // 5. 关于与自动检查更新
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BlackBrandLogo(size = 44.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("关于软件 I", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = "版本: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "核心引擎: $xrayVersion + hev-socks5-tunnel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onCheckUpdate,
                        enabled = !isCheckingUpdate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isCheckingUpdate) "正在检查版本更新..." else "检查软件更新")
                    }

                    Text(
                        text = "小技巧：可在手机顶部系统下拉快捷开关栏编辑按钮，将「I」磁贴拖到常用位置，无需打开 App 即可一键开关代理。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showImportBackupDialog) {
        var jsonInput by remember { mutableStateOf("") }
        IProxyAlertDialog(
            onDismissRequest = { showImportBackupDialog = false },
            title = { Text("恢复备份 JSON") },
            text = {
                OutlinedTextField(
                    value = jsonInput,
                    onValueChange = { jsonInput = it },
                    label = { Text("粘贴之前导出的备份 JSON") },
                    minLines = 5,
                    maxLines = 10,
                    colors = darkOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = jsonInput.trim().startsWith("{"),
                    onClick = {
                        onImportBackup(jsonInput.trim())
                        showImportBackupDialog = false
                    }
                ) {
                    Text("确认恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportBackupDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun Int.takeIn(min: Int, max: Int): Int? = if (this in min..max) this else null
