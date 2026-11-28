package com.iproxy.app.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iproxy.app.BuildConfig
import com.iproxy.app.data.model.AppSettings
import com.iproxy.app.ui.components.BlackBrandLogo
import com.iproxy.app.ui.components.GlassActionButton
import com.iproxy.app.ui.components.GlassDialog
import com.iproxy.app.ui.components.GlassDivider
import com.iproxy.app.ui.components.GlassGhostButton
import com.iproxy.app.ui.components.GlassLoadingIndicator
import com.iproxy.app.ui.components.GlassSecondaryButton
import com.iproxy.app.ui.components.GlassTextField
import com.iproxy.app.ui.components.LiquidGlassCard
import com.iproxy.app.ui.components.ProtoTextMuted
import com.iproxy.app.ui.components.ProtoTextPrimary
import com.iproxy.app.ui.components.ProtoTextSubtle
import com.iproxy.app.ui.components.PrototypeSwitch
import com.iproxy.app.ui.components.drawGlassCapsule3D
import kotlinx.coroutines.launch
import libv2ray.Libv2ray

/**
 * 设置页：全面采用 3D 蓝色实体玻璃材质与持续流动蓝色星链粒子，开启/关闭状态均有动效且一目了然。
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    isCheckingUpdate: Boolean,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onCheckUpdate: () -> Unit,
    onExportBackup: suspend () -> String,
    onImportBackup: (String) -> Unit,
    onCopyText: (String, String) -> Unit,
    onShowMessage: (String) -> Unit
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "设置",
            fontSize = 24.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.Bold,
            color = ProtoTextPrimary,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Text(
            text = "暗黑界面 · 核心参数与备份",
            fontSize = 13.sp,
            color = ProtoTextMuted,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
        )

        // 1. 核心连接与协议开关（每行开关在 ON/OFF 态均有实体玻璃与粒子流）
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, particleCount = 28, seedOffsetSec = 0.2f) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingToggleRow(
                    title = "断线后自动重连",
                    subtitle = "网络波动恢复时自动重新建立加密通道",
                    checked = settings.autoReconnect,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(autoReconnect = v) } }
                )
                GlassDivider()
                SettingToggleRow(
                    title = "局域网共享代理",
                    subtitle = "监听 0.0.0.0:${settings.socksPort}/${settings.httpPort}，允许同 Wi-Fi 设备共享连接",
                    checked = settings.allowLanShare,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(allowLanShare = v) } }
                )
                GlassDivider()
                SettingToggleRow(
                    title = "仅本地代理模式（不开 VPN）",
                    subtitle = "仅开启本地 SOCKS5/HTTP 端口，适合配合其他系统代理使用",
                    checked = settings.proxyOnlyMode,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(proxyOnlyMode = v) } }
                )
                GlassDivider()
                SettingToggleRow(
                    title = "启用 IPv6 路由",
                    subtitle = "若当地网络和节点均支持 IPv6 可开启",
                    checked = settings.enableIpv6,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(enableIpv6 = v) } }
                )
                GlassDivider()
                SettingToggleRow(
                    title = "FakeDNS 加速",
                    subtitle = "减少 DNS 查询延迟，提升网页首包速度",
                    checked = settings.enableFakeDns,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(enableFakeDns = v) } }
                )
                GlassDivider()
                SettingToggleRow(
                    title = "Mux 多路复用",
                    subtitle = "合并并发 TCP 连接（不适用于 XTLS Vision 与 XHTTP）",
                    checked = settings.enableMux,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(enableMux = v) } }
                )
                GlassDivider()
                SettingToggleRow(
                    title = "TLS Fragment 分片防阻断",
                    subtitle = "将 TLS 握手分片发送以降低特定网络干扰",
                    checked = settings.enableFragment,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(enableFragment = v) } }
                )
            }
        }

        // 2. DNS 与本地端口
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, particleCount = 26, seedOffsetSec = 1.1f) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("DNS 与本地端口", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ProtoTextPrimary)

                GlassTextField(
                    value = remoteDns,
                    onValueChange = { remoteDns = it },
                    label = "境外代理 DNS (例如 1.1.1.1 或 8.8.8.8)",
                    modifier = Modifier.fillMaxWidth()
                )
                GlassTextField(
                    value = directDns,
                    onValueChange = { directDns = it },
                    label = "国内直连 DNS (例如 223.5.5.5)",
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField(
                        value = socksPortStr,
                        onValueChange = { socksPortStr = it.filter { c -> c.isDigit() } },
                        label = "SOCKS5",
                        modifier = Modifier.weight(1f)
                    )
                    GlassTextField(
                        value = httpPortStr,
                        onValueChange = { httpPortStr = it.filter { c -> c.isDigit() } },
                        label = "HTTP",
                        modifier = Modifier.weight(1f)
                    )
                    GlassTextField(
                        value = mtuStr,
                        onValueChange = { mtuStr = it.filter { c -> c.isDigit() } },
                        label = "MTU",
                        modifier = Modifier.weight(1f)
                    )
                }
                GlassTextField(
                    value = speedUrl,
                    onValueChange = { speedUrl = it },
                    label = "真连接测速目标 URL",
                    modifier = Modifier.fillMaxWidth()
                )
                GlassActionButton(
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
                        onShowMessage("已保存网络参数")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存网络参数", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 3. 备份与工具操作行
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, particleCount = 20, seedOffsetSec = 2.0f) {
            Column(modifier = Modifier.fillMaxWidth()) {
                UtilityActionRow(
                    title = "导出全部节点与配置备份",
                    onClick = {
                        scope.launch {
                            val json = onExportBackup()
                            onCopyText(json, "已导出全部节点与设置备份 JSON 到剪贴板")
                        }
                    }
                )
                GlassDivider()
                UtilityActionRow(
                    title = "从备份 JSON 恢复配置",
                    onClick = { showImportBackupDialog = true }
                )
            }
        }

        // 4. 关于与检查更新
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, particleCount = 24, seedOffsetSec = 2.8f) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BlackBrandLogo(size = 44.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("关于软件 I", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ProtoTextPrimary)
                        Text(
                            text = "版本: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = ProtoTextPrimary
                        )
                        Text(
                            text = "核心引擎: $xrayVersion + hev-socks5-tunnel",
                            style = MaterialTheme.typography.bodySmall,
                            color = ProtoTextMuted
                        )
                    }
                }

                GlassSecondaryButton(
                    onClick = onCheckUpdate,
                    enabled = !isCheckingUpdate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCheckingUpdate) {
                        GlassLoadingIndicator(compact = true)
                    } else {
                        Icon(
                            Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = Color(0xFFB6E9FF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCheckingUpdate) "正在检查版本更新..." else "检查软件更新",
                        fontWeight = FontWeight.SemiBold,
                        color = ProtoTextPrimary
                    )
                }

                Text(
                    text = "提示：可在手机顶部系统下拉快捷开关栏编辑按钮，将「I」磁贴拖到常用位置，一键开关代理。",
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = ProtoTextSubtle
                )
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding())
        Spacer(modifier = Modifier.height(112.dp))
    }

    if (showImportBackupDialog) {
        var jsonInput by remember { mutableStateOf("") }
        GlassDialog(
            onDismissRequest = { showImportBackupDialog = false },
            title = "恢复备份 JSON",
            content = {
                GlassTextField(
                    value = jsonInput,
                    onValueChange = { jsonInput = it },
                    label = "粘贴之前导出的备份 JSON",
                    singleLine = false,
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            buttons = {
                GlassGhostButton(onClick = { showImportBackupDialog = false }) {
                    Text("取消")
                }
                GlassActionButton(
                    enabled = jsonInput.trim().startsWith("{"),
                    onClick = {
                        onImportBackup(jsonInput.trim())
                        showImportBackupDialog = false
                    }
                ) {
                    Text("确认恢复", fontWeight = FontWeight.SemiBold)
                }
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
            .heightIn(min = 65.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 15.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ProtoTextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF92A7BF))
        }
        Spacer(modifier = Modifier.width(12.dp))
        PrototypeSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun UtilityActionRow(
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 14.sp, color = ProtoTextPrimary, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(9.dp))
                .drawBehind {
                    drawGlassCapsule3D(cornerRadiusDp = 9.dp, active = false, innerBevelInsetDp = 1.5.dp)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF9AD5FF),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun Int.takeIn(min: Int, max: Int): Int? = if (this in min..max) this else null
