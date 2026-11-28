package com.iproxy.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonParser
import com.iproxy.app.data.model.PolicyStrategy
import com.iproxy.app.data.model.ProtocolType
import com.iproxy.app.data.model.SecurityMode
import com.iproxy.app.data.model.ServerNode
import com.iproxy.app.data.model.Subscription
import com.iproxy.app.data.model.TransportNetwork
import com.iproxy.app.ui.components.GlassActionButton
import com.iproxy.app.ui.components.GlassCheckbox
import com.iproxy.app.ui.components.GlassDialog
import com.iproxy.app.ui.components.GlassDivider
import com.iproxy.app.ui.components.GlassGhostButton
import com.iproxy.app.ui.components.GlassSecondaryButton
import com.iproxy.app.ui.components.GlassSelectableChip
import com.iproxy.app.ui.components.GlassSmallIconButton
import com.iproxy.app.ui.components.GlassTextField
import com.iproxy.app.ui.components.LiquidGlassCard
import com.iproxy.app.ui.components.ProtoTextMuted
import com.iproxy.app.ui.components.ProtoTextPrimary
import com.iproxy.app.ui.components.PrototypeSwitch

/**
 * 自定义 Xray 配置 / 自定义出站编辑器弹窗（3D 蓝色玻璃容器与星链流动粒子）
 */
@Composable
fun CustomJsonEditorDialog(
    isOutbound: Boolean,
    initial: ServerNode?,
    onDismiss: () -> Unit,
    onSave: (ServerNode) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var rawJson by remember { mutableStateOf(initial?.customRawJson ?: "") }
    var fileLoadError by remember { mutableStateOf<String?>(null) }

    val jsonFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                .orEmpty()
            if (content.isBlank()) error("文件内容为空")
            rawJson = content
            fileLoadError = null
        }.onFailure {
            fileLoadError = "读取文件失败: ${it.message}"
        }
    }

    val validationError = remember(rawJson, isOutbound) {
        validateCustomJson(rawJson, isOutbound)
    }

    val titleText = when {
        initial != null && isOutbound -> "编辑自定义出站"
        initial != null -> "编辑自定义配置"
        isOutbound -> "添加自定义出站"
        else -> "添加自定义配置"
    }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = titleText,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "节点名称",
                    placeholder = if (isOutbound) "我的自定义出站" else "我的自定义配置",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = if (isOutbound) {
                        "粘贴单个 Xray Outbound JSON 对象或出站链数组 [{...}]，将自动接入软件内置分流、DNS 与 TUN；也可在策略组或链式代理中引用。"
                    } else {
                        "粘贴完整 Xray JSON 配置文件（包含 outbounds / routing 等）。未声明 inbounds 时将自动补充本地入站。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = ProtoTextMuted
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassSecondaryButton(
                        onClick = { jsonFileLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("导入 .json", fontSize = 12.sp)
                    }
                    GlassSecondaryButton(
                        onClick = {
                            rawJson = if (isOutbound) SAMPLE_OUTBOUND_JSON else SAMPLE_FULL_CONFIG_JSON
                            fileLoadError = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("填入示例模板", fontSize = 12.sp)
                    }
                }

                if (!fileLoadError.isNullOrBlank()) {
                    Text(
                        text = fileLoadError.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFF87171)
                    )
                }

                GlassTextField(
                    value = rawJson,
                    onValueChange = {
                        rawJson = it
                        fileLoadError = null
                    },
                    label = if (isOutbound) "Outbound JSON" else "完整 Xray JSON",
                    singleLine = false,
                    minLines = 7,
                    maxLines = 14,
                    isError = rawJson.isNotBlank() && validationError != null,
                    modifier = Modifier.fillMaxWidth()
                )

                if (rawJson.isNotBlank() && validationError != null) {
                    Text(
                        text = validationError,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFF87171)
                    )
                }
            }
        },
        buttons = {
            GlassGhostButton(onClick = onDismiss) { Text("取消") }
            GlassActionButton(
                enabled = rawJson.isNotBlank() && validationError == null,
                onClick = {
                    val targetProtocol = if (isOutbound) ProtocolType.CUSTOM_OUTBOUND else ProtocolType.CUSTOM_JSON
                    val fallbackName = if (isOutbound) "自定义出站" else "自定义配置"
                    val base = initial ?: ServerNode(
                        name = name.ifBlank { fallbackName },
                        protocol = targetProtocol,
                        server = if (isOutbound) "outbound" else "custom",
                        port = 443
                    )
                    onSave(
                        base.copy(
                            name = name.trim().ifBlank { fallbackName },
                            protocol = targetProtocol,
                            server = if (isOutbound) "outbound" else "custom",
                            port = 443,
                            customRawJson = rawJson.trim()
                        )
                    )
                }
            ) {
                Text("保存", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

/**
 * 策略组编辑器弹窗（支持 最低延迟 / 故障转移 / 负载均衡 / 轮询 / 随机 + 子节点 + 订阅动态筛选）
 */
@Composable
fun PolicyGroupEditorDialog(
    initial: ServerNode?,
    allNodes: List<ServerNode>,
    subscriptions: List<Subscription>,
    onDismiss: () -> Unit,
    onSave: (ServerNode) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var strategy by remember { mutableStateOf(initial?.policyStrategy ?: PolicyStrategy.LEAST_PING) }
    val selectedChildIds = remember {
        mutableStateListOf<String>().apply {
            addAll(initial?.childNodeIds.orEmpty())
        }
    }
    var policySubId by remember { mutableStateOf(initial?.policySubscriptionId ?: "") }
    var policyFilter by remember { mutableStateOf(initial?.policyFilter ?: "") }
    var showNodePicker by remember { mutableStateOf(false) }

    val nodeMap = remember(allNodes) { allNodes.associateBy { it.id } }
    val eligibleNodes = remember(allNodes, initial?.id) {
        allNodes.filter {
            it.id != initial?.id &&
                it.protocol != ProtocolType.CUSTOM_JSON &&
                !wouldCreateCycle(candidateId = it.id, parentId = initial?.id, nodeMap = nodeMap)
        }
    }

    val isValid = name.isNotBlank() && (selectedChildIds.isNotEmpty() || policySubId.isNotBlank())

    GlassDialog(
        onDismissRequest = onDismiss,
        title = if (initial == null) "添加策略组" else "编辑策略组",
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "策略组名称",
                    placeholder = "例如：香港自动优选",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "调度策略",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = ProtoTextPrimary
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PolicyStrategy.entries.forEachIndexed { idx, s ->
                        GlassSelectableChip(
                            label = s.displayName,
                            selected = strategy == s,
                            seedOffsetSec = idx * 0.6f,
                            onClick = { strategy = s }
                        )
                    }
                }

                GlassDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "固定子节点 (${selectedChildIds.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ProtoTextPrimary
                    )
                    GlassSecondaryButton(
                        onClick = { showNodePicker = true },
                        enabled = eligibleNodes.isNotEmpty()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("选择节点", fontSize = 12.sp)
                    }
                }

                if (selectedChildIds.isEmpty()) {
                    Text(
                        text = if (eligibleNodes.isEmpty()) {
                            "当前没有可选节点，请先导入节点或使用下方订阅筛选"
                        } else {
                            "点击右侧「选择节点」将多个节点纳入策略组（故障转移模式下按列表顺序优先回退）"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ProtoTextMuted
                    )
                } else {
                    OrderedChildNodesList(
                        childIds = selectedChildIds,
                        nodeMap = nodeMap,
                        labelPrefix = { idx, _ -> "#${idx + 1}" }
                    )
                }

                if (subscriptions.isNotEmpty()) {
                    GlassDivider()
                    Text(
                        text = "订阅动态纳入（可选）",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ProtoTextPrimary
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GlassSelectableChip(
                            label = "不启用",
                            selected = policySubId.isBlank(),
                            seedOffsetSec = 0.2f,
                            onClick = { policySubId = "" }
                        )
                        GlassSelectableChip(
                            label = "全部节点",
                            selected = policySubId == "ALL",
                            seedOffsetSec = 0.8f,
                            onClick = { policySubId = "ALL" }
                        )
                        subscriptions.forEachIndexed { idx, sub ->
                            GlassSelectableChip(
                                label = sub.name,
                                selected = policySubId == sub.id,
                                seedOffsetSec = 1.4f + idx * 0.6f,
                                onClick = { policySubId = sub.id }
                            )
                        }
                    }
                    if (policySubId.isNotBlank()) {
                        GlassTextField(
                            value = policyFilter,
                            onValueChange = { policyFilter = it },
                            label = "名称关键词过滤（可选）",
                            placeholder = "例如：香港 / HK / IEPL",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        buttons = {
            GlassGhostButton(onClick = onDismiss) { Text("取消") }
            GlassActionButton(
                enabled = isValid,
                onClick = {
                    val base = initial ?: ServerNode(
                        name = name.trim(),
                        protocol = ProtocolType.POLICY_GROUP,
                        server = "group",
                        port = 443
                    )
                    onSave(
                        base.copy(
                            name = name.trim(),
                            protocol = ProtocolType.POLICY_GROUP,
                            server = "group",
                            port = 443,
                            policyStrategy = strategy,
                            childNodeIds = selectedChildIds.toList(),
                            policySubscriptionId = policySubId,
                            policyFilter = policyFilter.trim()
                        )
                    )
                }
            ) {
                Text("保存", fontWeight = FontWeight.SemiBold)
            }
        }
    )

    if (showNodePicker) {
        MultiNodePickerDialog(
            title = "选择策略组子节点",
            candidates = eligibleNodes,
            initialSelectedIds = selectedChildIds.toSet(),
            allowDuplicates = false,
            onDismiss = { showNodePicker = false },
            onConfirm = { pickedIds ->
                val keptOrder = selectedChildIds.filter { it in pickedIds }
                val newlyAdded = pickedIds.filter { it !in keptOrder }
                selectedChildIds.clear()
                selectedChildIds.addAll(keptOrder)
                selectedChildIds.addAll(newlyAdded)
                showNodePicker = false
            }
        )
    }
}

/**
 * 链式代理编辑器弹窗（按 入口 -> 中继 -> 出口 顺序构造 multi-hop dialerProxy）
 */
@Composable
fun ProxyChainEditorDialog(
    initial: ServerNode?,
    allNodes: List<ServerNode>,
    onDismiss: () -> Unit,
    onSave: (ServerNode) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    val selectedHopIds = remember {
        mutableStateListOf<String>().apply {
            addAll(initial?.childNodeIds.orEmpty())
        }
    }
    var showHopPicker by remember { mutableStateOf(false) }

    val nodeMap = remember(allNodes) { allNodes.associateBy { it.id } }
    val eligibleNodes = remember(allNodes, initial?.id) {
        allNodes.filter {
            it.id != initial?.id &&
                it.protocol != ProtocolType.CUSTOM_JSON &&
                !wouldCreateCycle(candidateId = it.id, parentId = initial?.id, nodeMap = nodeMap)
        }
    }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = if (initial == null) "添加链式代理" else "编辑链式代理",
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "链式代理名称",
                    placeholder = "例如：国内中转 -> 香港落地",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "流量将按自上而下顺序依次穿透：第 1 跳为本机直连的前置入口节点，最后一跳为最终落地出口节点。",
                    style = MaterialTheme.typography.bodySmall,
                    color = ProtoTextMuted
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "代理链路 (${selectedHopIds.size} 跳)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ProtoTextPrimary
                    )
                    GlassSecondaryButton(
                        onClick = { showHopPicker = true },
                        enabled = eligibleNodes.isNotEmpty()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加一跳", fontSize = 12.sp)
                    }
                }

                if (selectedHopIds.isEmpty()) {
                    Text(
                        text = if (eligibleNodes.isEmpty()) {
                            "当前没有可选节点，请先添加或导入节点"
                        } else {
                            "请点击「添加一跳」按顺序添加前置入口节点与出口落地节点"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ProtoTextMuted
                    )
                } else {
                    OrderedChildNodesList(
                        childIds = selectedHopIds,
                        nodeMap = nodeMap,
                        labelPrefix = { idx, total ->
                            when {
                                total == 1 -> "单跳"
                                idx == 0 -> "入口 #1"
                                idx == total - 1 -> "出口 #${idx + 1}"
                                else -> "中继 #${idx + 1}"
                            }
                        }
                    )
                }

                if (selectedHopIds.size == 1) {
                    Text(
                        text = "提示：链式代理建议至少包含 2 跳（前置入口 -> 出口落地）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4BE8FF)
                    )
                }
            }
        },
        buttons = {
            GlassGhostButton(onClick = onDismiss) { Text("取消") }
            GlassActionButton(
                enabled = name.isNotBlank() && selectedHopIds.isNotEmpty(),
                onClick = {
                    val base = initial ?: ServerNode(
                        name = name.trim(),
                        protocol = ProtocolType.PROXY_CHAIN,
                        server = "chain",
                        port = 443
                    )
                    onSave(
                        base.copy(
                            name = name.trim(),
                            protocol = ProtocolType.PROXY_CHAIN,
                            server = "chain",
                            port = 443,
                            childNodeIds = selectedHopIds.toList()
                        )
                    )
                }
            ) {
                Text("保存", fontWeight = FontWeight.SemiBold)
            }
        }
    )

    if (showHopPicker) {
        MultiNodePickerDialog(
            title = "选择下一跳节点",
            candidates = eligibleNodes,
            initialSelectedIds = emptySet(),
            allowDuplicates = true,
            onDismiss = { showHopPicker = false },
            onConfirm = { pickedIds ->
                selectedHopIds.addAll(pickedIds)
                showHopPicker = false
            }
        )
    }
}

/**
 * 通用协议节点编辑器（全面覆盖 VMess / VLESS / Shadowsocks / Trojan / Hysteria2 / WireGuard / MASQUE / SOCKS / HTTP）：
 * 所有协议芯片、传输方式、安全模式、输入框、开关、复选框与保存按钮均统一升级为 3D 蓝色玻璃与流动粒子。
 */
@Composable
fun ProtocolNodeEditorDialog(
    initialProtocol: ProtocolType,
    initialNode: ServerNode?,
    onDismiss: () -> Unit,
    onSave: (ServerNode) -> Unit
) {
    var protocol by remember { mutableStateOf(initialNode?.protocol ?: initialProtocol) }
    var name by remember { mutableStateOf(initialNode?.name ?: "") }
    var server by remember { mutableStateOf(initialNode?.server ?: "") }
    var portStr by remember {
        mutableStateOf(
            initialNode?.port?.toString() ?: defaultPortForProtocol(initialProtocol).toString()
        )
    }
    var username by remember { mutableStateOf(initialNode?.username ?: "") }
    var credential by remember { mutableStateOf(initialNode?.passwordOrUuid ?: "") }
    var method by remember {
        mutableStateOf(
            initialNode?.method ?: if (initialProtocol == ProtocolType.VMESS) "auto" else "aes-128-gcm"
        )
    }
    var flow by remember { mutableStateOf(initialNode?.flow ?: "") }
    var network by remember { mutableStateOf(initialNode?.network ?: TransportNetwork.TCP) }
    var headerType by remember { mutableStateOf(initialNode?.headerType ?: "none") }
    var host by remember { mutableStateOf(initialNode?.host ?: "") }
    var path by remember { mutableStateOf(initialNode?.path ?: "") }
    var xhttpMode by remember { mutableStateOf(initialNode?.xhttpMode ?: "") }
    var grpcServiceName by remember { mutableStateOf(initialNode?.grpcServiceName ?: "") }
    var security by remember {
        mutableStateOf(
            initialNode?.security ?: when (initialProtocol) {
                ProtocolType.TROJAN, ProtocolType.HYSTERIA2, ProtocolType.MASQUE -> SecurityMode.TLS
                ProtocolType.VLESS -> SecurityMode.TLS
                else -> SecurityMode.NONE
            }
        )
    }
    var sni by remember { mutableStateOf(initialNode?.sni ?: "") }
    var alpn by remember { mutableStateOf(initialNode?.alpn ?: "") }
    var fingerprint by remember { mutableStateOf(initialNode?.fingerprint ?: "") }
    var allowInsecure by remember { mutableStateOf(initialNode?.allowInsecure ?: false) }
    var pinSha256 by remember { mutableStateOf(initialNode?.pinSha256 ?: "") }
    var realityPbk by remember { mutableStateOf(initialNode?.realityPublicKey ?: "") }
    var realitySid by remember { mutableStateOf(initialNode?.realityShortId ?: "") }
    var realitySpx by remember { mutableStateOf(initialNode?.realitySpiderX ?: "") }

    // Hysteria2 专用
    var hy2ObfsPass by remember { mutableStateOf(initialNode?.hy2ObfsPassword ?: "") }
    var hy2PortHopping by remember { mutableStateOf(initialNode?.hy2PortHopping ?: "") }
    var hy2UpStr by remember { mutableStateOf(initialNode?.hy2UpMbps?.takeIf { it > 0 }?.toString() ?: "") }
    var hy2DownStr by remember { mutableStateOf(initialNode?.hy2DownMbps?.takeIf { it > 0 }?.toString() ?: "") }

    // WireGuard 专用
    var wgPrivateKey by remember { mutableStateOf(initialNode?.wgPrivateKey ?: "") }
    var wgPublicKey by remember { mutableStateOf(initialNode?.wgPublicKey ?: "") }
    var wgPresharedKey by remember { mutableStateOf(initialNode?.wgPresharedKey ?: "") }
    var wgLocalAddress by remember { mutableStateOf(initialNode?.wgLocalAddress ?: "10.0.0.2/32") }
    var wgReserved by remember { mutableStateOf(initialNode?.wgReserved ?: "") }
    var wgMtuStr by remember { mutableStateOf((initialNode?.wgMtu ?: 1420).toString()) }

    // MASQUE 专用
    var masquePath by remember { mutableStateOf(initialNode?.masquePath ?: "") }
    var masqueHeaders by remember { mutableStateOf(initialNode?.masqueHeaders ?: "") }
    var masqueRemoteDns by remember { mutableStateOf(initialNode?.masqueRemoteDns ?: "") }
    var useMasqueWarp by remember {
        mutableStateOf(!initialNode?.masqueWarpPrivateKey.isNullOrBlank())
    }
    var masqueWarpPk by remember { mutableStateOf(initialNode?.masqueWarpPrivateKey ?: "") }
    var masqueWarpPub by remember { mutableStateOf(initialNode?.masqueWarpPublicKey ?: "") }
    var masqueWarpAddr by remember {
        mutableStateOf(initialNode?.masqueWarpAddress?.ifBlank { "172.16.0.2/32" } ?: "172.16.0.2/32")
    }

    val selectableProtocols = listOf(
        ProtocolType.VMESS,
        ProtocolType.VLESS,
        ProtocolType.SHADOWSOCKS,
        ProtocolType.TROJAN,
        ProtocolType.HYSTERIA2,
        ProtocolType.WIREGUARD,
        ProtocolType.MASQUE,
        ProtocolType.SOCKS,
        ProtocolType.HTTP
    )

    val validPort = portStr.toIntOrNull()?.takeIf { it in 1..65535 }
        ?: defaultPortForProtocol(protocol)
    val isValid = server.isNotBlank() && when (protocol) {
        ProtocolType.WIREGUARD -> wgPrivateKey.isNotBlank() && wgPublicKey.isNotBlank()
        ProtocolType.VLESS, ProtocolType.VMESS -> credential.isNotBlank()
        else -> true
    }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = if (initialNode == null) "添加 [${protocol.displayName}]" else "编辑 [${protocol.displayName}]",
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectableProtocols.forEachIndexed { idx, p ->
                        GlassSelectableChip(
                            label = p.displayName,
                            selected = protocol == p,
                            seedOffsetSec = idx * 0.45f,
                            onClick = {
                                if (protocol != p) {
                                    val wasDefaultPort = portStr == defaultPortForProtocol(protocol).toString()
                                    protocol = p
                                    if (initialNode == null && wasDefaultPort) {
                                        portStr = defaultPortForProtocol(p).toString()
                                    }
                                    if (p == ProtocolType.HYSTERIA2 || p == ProtocolType.MASQUE) {
                                        security = SecurityMode.TLS
                                    }
                                }
                            }
                        )
                    }
                }

                GlassTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "节点备注名称（可选）",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                GlassTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = "服务器地址 (域名或 IP)",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                GlassTextField(
                    value = portStr,
                    onValueChange = { portStr = it.filter { ch -> ch.isDigit() } },
                    label = "端口",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                when (protocol) {
                    ProtocolType.VLESS, ProtocolType.VMESS, ProtocolType.TROJAN -> {
                        GlassTextField(
                            value = credential,
                            onValueChange = { credential = it },
                            label = if (protocol == ProtocolType.TROJAN) "密码" else "UUID",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (protocol == ProtocolType.VLESS) {
                            GlassTextField(
                                value = flow,
                                onValueChange = { flow = it },
                                label = "流控 Flow（可选，如 xtls-rprx-vision）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (protocol == ProtocolType.VMESS) {
                            GlassTextField(
                                value = method,
                                onValueChange = { method = it },
                                label = "加密方式 (auto / aes-128-gcm / chacha20-poly1305 / none)",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Text("传输协议 (network)", style = MaterialTheme.typography.labelMedium, color = ProtoTextPrimary)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                TransportNetwork.TCP,
                                TransportNetwork.WS,
                                TransportNetwork.HTTPUPGRADE,
                                TransportNetwork.XHTTP,
                                TransportNetwork.GRPC
                            ).forEachIndexed { idx, net ->
                                GlassSelectableChip(
                                    label = net.value.uppercase(),
                                    selected = network == net,
                                    seedOffsetSec = idx * 0.55f,
                                    onClick = { network = net }
                                )
                            }
                        }
                        if (network == TransportNetwork.WS ||
                            network == TransportNetwork.HTTPUPGRADE ||
                            network == TransportNetwork.XHTTP
                        ) {
                            GlassTextField(
                                value = host,
                                onValueChange = { host = it },
                                label = "伪装域名 Host（可选）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            GlassTextField(
                                value = path,
                                onValueChange = { path = it },
                                label = "路径 Path（如 /ws）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (network == TransportNetwork.XHTTP) {
                                GlassTextField(
                                    value = xhttpMode,
                                    onValueChange = { xhttpMode = it },
                                    label = "XHTTP 模式（auto / packet-up / stream-up / stream-one）",
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else if (network == TransportNetwork.GRPC) {
                            GlassTextField(
                                value = grpcServiceName,
                                onValueChange = { grpcServiceName = it },
                                label = "gRPC serviceName",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Text("传输安全 (security)", style = MaterialTheme.typography.labelMedium, color = ProtoTextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SecurityMode.entries.forEachIndexed { idx, sec ->
                                GlassSelectableChip(
                                    label = sec.value.uppercase(),
                                    selected = security == sec,
                                    seedOffsetSec = idx * 0.65f,
                                    onClick = { security = sec }
                                )
                            }
                        }
                        if (security != SecurityMode.NONE) {
                            GlassTextField(
                                value = sni,
                                onValueChange = { sni = it },
                                label = "SNI / ServerName（可选）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            GlassTextField(
                                value = fingerprint,
                                onValueChange = { fingerprint = it },
                                label = "uTLS 指纹（可选，如 chrome / ios）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (security == SecurityMode.TLS) {
                            GlassTextField(
                                value = alpn,
                                onValueChange = { alpn = it },
                                label = "ALPN（可选，如 h2,http/1.1）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            InsecureCertToggleRow(
                                checked = allowInsecure,
                                onCheckedChange = { allowInsecure = it }
                            )
                        } else if (security == SecurityMode.REALITY) {
                            GlassTextField(
                                value = realityPbk,
                                onValueChange = { realityPbk = it },
                                label = "Reality PublicKey (pbk)",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            GlassTextField(
                                value = realitySid,
                                onValueChange = { realitySid = it },
                                label = "Reality ShortId (sid)",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            GlassTextField(
                                value = realitySpx,
                                onValueChange = { realitySpx = it },
                                label = "Reality SpiderX（可选）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    ProtocolType.SHADOWSOCKS -> {
                        GlassTextField(
                            value = method,
                            onValueChange = { method = it },
                            label = "加密方式 (如 aes-256-gcm / 2022-blake3-aes-128-gcm)",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "aes-128-gcm",
                                "aes-256-gcm",
                                "chacha20-ietf-poly1305",
                                "2022-blake3-aes-128-gcm"
                            ).forEachIndexed { idx, cipher ->
                                GlassSelectableChip(
                                    label = cipher,
                                    selected = method == cipher,
                                    seedOffsetSec = idx * 0.5f,
                                    onClick = { method = cipher }
                                )
                            }
                        }
                        GlassTextField(
                            value = credential,
                            onValueChange = { credential = it },
                            label = "密码",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ProtocolType.HYSTERIA2 -> {
                        GlassTextField(
                            value = credential,
                            onValueChange = { credential = it },
                            label = "认证密码 (auth)",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        GlassTextField(
                            value = sni,
                            onValueChange = { sni = it },
                            label = "SNI / 伪装域名（可选）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        GlassTextField(
                            value = hy2ObfsPass,
                            onValueChange = { hy2ObfsPass = it },
                            label = "Salamander 混淆密码（可选）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        GlassTextField(
                            value = hy2PortHopping,
                            onValueChange = { hy2PortHopping = it },
                            label = "端口跳跃范围 mport（可选，如 20000-30000）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassTextField(
                                value = hy2UpStr,
                                onValueChange = { hy2UpStr = it.filter(Char::isDigit) },
                                label = "上行 Mbps",
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            GlassTextField(
                                value = hy2DownStr,
                                onValueChange = { hy2DownStr = it.filter(Char::isDigit) },
                                label = "下行 Mbps",
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        GlassTextField(
                            value = pinSha256,
                            onValueChange = { pinSha256 = it },
                            label = "证书指纹 PinSHA256（可选）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        InsecureCertToggleRow(
                            checked = allowInsecure,
                            onCheckedChange = { allowInsecure = it }
                        )
                    }

                    ProtocolType.WIREGUARD -> {
                        GlassTextField(
                            value = wgPrivateKey,
                            onValueChange = { wgPrivateKey = it },
                            label = "本机私钥 PrivateKey (Base64)",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        GlassTextField(
                            value = wgPublicKey,
                            onValueChange = { wgPublicKey = it },
                            label = "对端公钥 Peer PublicKey (Base64)",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        GlassTextField(
                            value = wgPresharedKey,
                            onValueChange = { wgPresharedKey = it },
                            label = "预共享密钥 PreSharedKey（可选）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        GlassTextField(
                            value = wgLocalAddress,
                            onValueChange = { wgLocalAddress = it },
                            label = "本地接口地址 CIDR（如 10.0.0.2/32）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassTextField(
                                value = wgMtuStr,
                                onValueChange = { wgMtuStr = it.filter(Char::isDigit) },
                                label = "MTU",
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            GlassTextField(
                                value = wgReserved,
                                onValueChange = { wgReserved = it },
                                label = "Reserved（可选 0,0,0）",
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    ProtocolType.MASQUE -> {
                        GlassTextField(
                            value = sni,
                            onValueChange = { sni = it },
                            label = "SNI / 证书域名（如 consumer-masque.cloudflareclient.com）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        GlassTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = "Host 请求头（可选，如 cloudflareaccess.com）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        GlassTextField(
                            value = masquePath,
                            onValueChange = { masquePath = it },
                            label = "Path（可选，默认 /.well-known/masque/ip/*/*/，WARP 用 /）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        GlassTextField(
                            value = masqueRemoteDns,
                            onValueChange = { masqueRemoteDns = it },
                            label = "远端 DNS IP（可选，逗号分隔）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { useMasqueWarp = !useMasqueWarp }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("启用 WARP-over-MASQUE 密钥参数", style = MaterialTheme.typography.bodyMedium, color = ProtoTextPrimary)
                            PrototypeSwitch(checked = useMasqueWarp, onCheckedChange = { useMasqueWarp = it })
                        }
                        if (useMasqueWarp) {
                            GlassTextField(
                                value = masqueWarpPk,
                                onValueChange = { masqueWarpPk = it },
                                label = "WARP PrivateKey (ECDSA P-256 Base64/PEM)",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            GlassTextField(
                                value = masqueWarpPub,
                                onValueChange = { masqueWarpPub = it },
                                label = "WARP PublicKey (ECDSA P-256 Base64/PEM)",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            GlassTextField(
                                value = masqueWarpAddr,
                                onValueChange = { masqueWarpAddr = it },
                                label = "WARP 虚拟地址 CIDR（逗号分隔）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = "Basic 用户名（可选）",
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                GlassTextField(
                                    value = credential,
                                    onValueChange = { credential = it },
                                    label = "Basic 密码（可选）",
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            GlassTextField(
                                value = masqueHeaders,
                                onValueChange = { masqueHeaders = it },
                                label = "自定义请求头（可选，每行 Key: Value）",
                                singleLine = false,
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        GlassTextField(
                            value = pinSha256,
                            onValueChange = { pinSha256 = it },
                            label = "证书指纹 PinSHA256（可选）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        InsecureCertToggleRow(
                            checked = allowInsecure,
                            onCheckedChange = { allowInsecure = it }
                        )
                    }

                    ProtocolType.SOCKS, ProtocolType.HTTP -> {
                        GlassTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = "用户名（可选）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        GlassTextField(
                            value = credential,
                            onValueChange = { credential = it },
                            label = "密码（可选）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (protocol == ProtocolType.HTTP) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        security = if (security == SecurityMode.TLS) SecurityMode.NONE else SecurityMode.TLS
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("启用 HTTPS (TLS)", style = MaterialTheme.typography.bodyMedium, color = ProtoTextPrimary)
                                PrototypeSwitch(
                                    checked = security == SecurityMode.TLS,
                                    onCheckedChange = {
                                        security = if (it) SecurityMode.TLS else SecurityMode.NONE
                                    }
                                )
                            }
                            if (security == SecurityMode.TLS) {
                                GlassTextField(
                                    value = sni,
                                    onValueChange = { sni = it },
                                    label = "SNI（可选）",
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                InsecureCertToggleRow(
                                    checked = allowInsecure,
                                    onCheckedChange = { allowInsecure = it }
                                )
                            }
                        }
                    }

                    else -> Unit
                }
            }
        },
        buttons = {
            GlassGhostButton(onClick = onDismiss) { Text("取消") }
            GlassActionButton(
                enabled = isValid,
                onClick = {
                    val savedName = name.trim().ifBlank { "${server.trim()}:$validPort" }
                    val resolvedNetwork = when (protocol) {
                        ProtocolType.HYSTERIA2 -> TransportNetwork.HYSTERIA
                        ProtocolType.MASQUE -> TransportNetwork.MASQUE
                        ProtocolType.VLESS, ProtocolType.VMESS, ProtocolType.TROJAN -> network
                        else -> TransportNetwork.TCP
                    }
                    val resolvedSecurity = when (protocol) {
                        ProtocolType.HYSTERIA2, ProtocolType.MASQUE -> SecurityMode.TLS
                        else -> security
                    }
                    val base = initialNode ?: ServerNode(
                        name = savedName,
                        protocol = protocol,
                        server = server.trim(),
                        port = validPort
                    )
                    onSave(
                        base.copy(
                            name = savedName,
                            protocol = protocol,
                            server = server.trim(),
                            port = validPort,
                            username = username.trim(),
                            passwordOrUuid = credential.trim(),
                            method = method.trim().ifBlank {
                                if (protocol == ProtocolType.VMESS) "auto" else "aes-128-gcm"
                            },
                            flow = flow.trim(),
                            network = resolvedNetwork,
                            headerType = headerType,
                            host = host.trim(),
                            path = path.trim(),
                            xhttpMode = xhttpMode.trim(),
                            grpcServiceName = grpcServiceName.trim(),
                            security = resolvedSecurity,
                            sni = sni.trim(),
                            alpn = alpn.trim(),
                            fingerprint = fingerprint.trim(),
                            allowInsecure = allowInsecure,
                            pinSha256 = pinSha256.trim(),
                            realityPublicKey = realityPbk.trim(),
                            realityShortId = realitySid.trim(),
                            realitySpiderX = realitySpx.trim(),
                            hy2ObfsType = if (hy2ObfsPass.isNotBlank()) "salamander" else "",
                            hy2ObfsPassword = hy2ObfsPass.trim(),
                            hy2PortHopping = hy2PortHopping.trim(),
                            hy2UpMbps = hy2UpStr.toIntOrNull() ?: 0,
                            hy2DownMbps = hy2DownStr.toIntOrNull() ?: 0,
                            wgPrivateKey = wgPrivateKey.trim(),
                            wgPublicKey = wgPublicKey.trim(),
                            wgPresharedKey = wgPresharedKey.trim(),
                            wgLocalAddress = wgLocalAddress.trim().ifBlank { "10.0.0.2/32" },
                            wgReserved = wgReserved.trim(),
                            wgMtu = wgMtuStr.toIntOrNull()?.coerceIn(1280, 65535) ?: 1420,
                            masquePath = masquePath.trim(),
                            masqueHeaders = masqueHeaders.trim(),
                            masqueRemoteDns = masqueRemoteDns.trim(),
                            masqueWarpPrivateKey = if (useMasqueWarp) masqueWarpPk.trim() else "",
                            masqueWarpPublicKey = if (useMasqueWarp) masqueWarpPub.trim() else "",
                            masqueWarpAddress = if (useMasqueWarp) masqueWarpAddr.trim() else ""
                        )
                    )
                }
            ) {
                Text("保存", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun InsecureCertToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassCheckbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(10.dp))
        Text("允许不安全 TLS 证书 (allowInsecure)", style = MaterialTheme.typography.bodySmall, color = ProtoTextPrimary)
    }
}

@Composable
private fun OrderedChildNodesList(
    childIds: MutableList<String>,
    nodeMap: Map<String, ServerNode>,
    labelPrefix: (index: Int, total: Int) -> String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        childIds.forEachIndexed { idx, id ->
            val target = nodeMap[id]
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                particleCount = 14,
                seedOffsetSec = idx * 0.5f
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = labelPrefix(idx, childIds.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4BE8FF),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = target?.name ?: "已失效节点 ($id)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = ProtoTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (target != null) {
                            Text(
                                text = target.badgeText(),
                                style = MaterialTheme.typography.labelSmall,
                                color = ProtoTextMuted,
                                maxLines = 1
                            )
                        }
                    }
                    GlassSmallIconButton(
                        icon = Icons.Rounded.ArrowUpward,
                        contentDescription = "上移",
                        enabled = idx > 0,
                        sizeDp = 28.dp,
                        cornerRadius = 8.dp,
                        onClick = {
                            if (idx > 0) {
                                val item = childIds.removeAt(idx)
                                childIds.add(idx - 1, item)
                            }
                        }
                    )
                    GlassSmallIconButton(
                        icon = Icons.Rounded.ArrowDownward,
                        contentDescription = "下移",
                        enabled = idx < childIds.lastIndex,
                        sizeDp = 28.dp,
                        cornerRadius = 8.dp,
                        onClick = {
                            if (idx < childIds.lastIndex) {
                                val item = childIds.removeAt(idx)
                                childIds.add(idx + 1, item)
                            }
                        }
                    )
                    GlassSmallIconButton(
                        icon = Icons.Rounded.Delete,
                        contentDescription = "移除",
                        sizeDp = 28.dp,
                        cornerRadius = 8.dp,
                        onClick = { childIds.removeAt(idx) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiNodePickerDialog(
    title: String,
    candidates: List<ServerNode>,
    initialSelectedIds: Set<String>,
    allowDuplicates: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val pickedOrder = remember {
        mutableStateListOf<String>().apply {
            if (!allowDuplicates) addAll(initialSelectedIds)
        }
    }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                candidates.forEachIndexed { idx, candidate ->
                    val isChecked = !allowDuplicates && candidate.id in pickedOrder
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 15.dp,
                        isActive = isChecked,
                        accentBorder = if (isChecked) Color(0xCC9F5EFF) else null,
                        seedOffsetSec = idx * 0.45f,
                        onClick = {
                            if (allowDuplicates) {
                                pickedOrder.add(candidate.id)
                                onConfirm(pickedOrder.toList())
                            } else {
                                if (!pickedOrder.remove(candidate.id)) {
                                    pickedOrder.add(candidate.id)
                                }
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!allowDuplicates) {
                                GlassCheckbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (candidate.id !in pickedOrder) pickedOrder.add(candidate.id)
                                        } else {
                                            pickedOrder.remove(candidate.id)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = candidate.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ProtoTextPrimary,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${candidate.badgeText()} · ${candidate.displayAddressSummary()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ProtoTextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (allowDuplicates) {
                                Icon(Icons.Rounded.Add, contentDescription = "添加", tint = Color(0xFF4BE8FF), modifier = Modifier.size(18.dp))
                            } else if (isChecked) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFFFFDDF6), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        buttons = {
            GlassGhostButton(onClick = onDismiss) { Text("关闭") }
            if (!allowDuplicates) {
                GlassActionButton(onClick = { onConfirm(pickedOrder.toList()) }) {
                    Text("确定 (${pickedOrder.size})", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    )
}

internal fun defaultPortForProtocol(protocol: ProtocolType): Int = when (protocol) {
    ProtocolType.WIREGUARD -> 51820
    ProtocolType.SHADOWSOCKS -> 8388
    ProtocolType.SOCKS -> 1080
    ProtocolType.HTTP -> 8080
    else -> 443
}

internal fun validateCustomJson(raw: String, isOutbound: Boolean): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "JSON 不能为空"
    val sanitizedForCheck = trimmed
        .replace("{{tag}}", "proxy")
        .replace("{{detour}}", "direct")
    val elem = runCatching { JsonParser.parseString(sanitizedForCheck) }.getOrElse { err ->
        return "JSON 语法错误: ${err.message}"
    }
    return if (isOutbound) {
        when {
            elem.isJsonObject -> {
                val obj = elem.asJsonObject
                if (!obj.has("protocol") && !obj.has("outbounds")) {
                    "请包含 protocol 字段的 Outbound JSON 对象"
                } else null
            }
            elem.isJsonArray -> {
                val arr = elem.asJsonArray
                if (arr.size() == 0 || arr.any { !it.isJsonObject || !it.asJsonObject.has("protocol") }) {
                    "Outbound 数组每项都需为包含 protocol 字段的 JSON 对象"
                } else null
            }
            else -> "自定义出站需为 JSON 对象或出站数组"
        }
    } else {
        if (!elem.isJsonObject) {
            "自定义配置需为 JSON 对象"
        } else {
            val obj = elem.asJsonObject
            if (!obj.has("outbounds") && !obj.has("protocol")) {
                "请包含 outbounds 数组或 protocol 字段"
            } else null
        }
    }
}

private fun wouldCreateCycle(
    candidateId: String,
    parentId: String?,
    nodeMap: Map<String, ServerNode>,
    visited: MutableSet<String> = mutableSetOf()
): Boolean {
    if (parentId == null) return false
    if (candidateId == parentId) return true
    if (!visited.add(candidateId)) return false
    val candidate = nodeMap[candidateId] ?: return false
    return candidate.childNodeIds.any { childId ->
        wouldCreateCycle(childId, parentId, nodeMap, visited)
    }
}

private const val SAMPLE_OUTBOUND_JSON = """{
  "tag": "{{tag}}",
  "protocol": "vless",
  "settings": {
    "address": "example.com",
    "port": 443,
    "id": "00000000-0000-0000-0000-000000000000",
    "encryption": "none"
  },
  "streamSettings": {
    "network": "tcp",
    "security": "tls"
  }
}"""

private const val SAMPLE_FULL_CONFIG_JSON = """{
  "log": { "loglevel": "warning" },
  "outbounds": [
    {
      "tag": "proxy",
      "protocol": "freedom"
    }
  ]
}"""
