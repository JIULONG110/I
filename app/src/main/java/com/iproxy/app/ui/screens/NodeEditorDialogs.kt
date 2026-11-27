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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParser
import com.iproxy.app.data.model.PolicyStrategy
import com.iproxy.app.data.model.ProtocolType
import com.iproxy.app.data.model.SecurityMode
import com.iproxy.app.data.model.ServerNode
import com.iproxy.app.data.model.Subscription
import com.iproxy.app.data.model.TransportNetwork

/**
 * 自定义 Xray 配置 / 自定义出站编辑器弹窗
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("节点名称") },
                    placeholder = { Text(if (isOutbound) "我的自定义出站" else "我的自定义配置") },
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { jsonFileLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("导入 .json 文件")
                    }
                    OutlinedButton(
                        onClick = {
                            rawJson = if (isOutbound) SAMPLE_OUTBOUND_JSON else SAMPLE_FULL_CONFIG_JSON
                            fileLoadError = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("填入示例模板")
                    }
                }

                if (!fileLoadError.isNullOrBlank()) {
                    Text(
                        text = fileLoadError.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                OutlinedTextField(
                    value = rawJson,
                    onValueChange = {
                        rawJson = it
                        fileLoadError = null
                    },
                    label = { Text(if (isOutbound) "Outbound JSON" else "完整 Xray JSON") },
                    minLines = 7,
                    maxLines = 14,
                    isError = rawJson.isNotBlank() && validationError != null,
                    modifier = Modifier.fillMaxWidth()
                )

                if (rawJson.isNotBlank() && validationError != null) {
                    Text(
                        text = validationError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
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
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加策略组" else "编辑策略组") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("策略组名称") },
                    placeholder = { Text("例如：香港自动优选") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "调度策略",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PolicyStrategy.entries.forEach { s ->
                        FilterChip(
                            selected = strategy == s,
                            onClick = { strategy = s },
                            label = { Text(s.displayName) }
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "固定子节点 (${selectedChildIds.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = { showNodePicker = true },
                        enabled = eligibleNodes.isNotEmpty()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("选择节点")
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OrderedChildNodesList(
                        childIds = selectedChildIds,
                        nodeMap = nodeMap,
                        labelPrefix = { idx, _ -> "#${idx + 1}" }
                    )
                }

                if (subscriptions.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = "订阅动态纳入（可选）",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = policySubId.isBlank(),
                            onClick = { policySubId = "" },
                            label = { Text("不启用") }
                        )
                        FilterChip(
                            selected = policySubId == "ALL",
                            onClick = { policySubId = "ALL" },
                            label = { Text("全部节点") }
                        )
                        subscriptions.forEach { sub ->
                            FilterChip(
                                selected = policySubId == sub.id,
                                onClick = { policySubId = sub.id },
                                label = { Text(sub.name) }
                            )
                        }
                    }
                    if (policySubId.isNotBlank()) {
                        OutlinedTextField(
                            value = policyFilter,
                            onValueChange = { policyFilter = it },
                            label = { Text("名称关键词过滤（可选）") },
                            placeholder = { Text("例如：香港 / HK / IEPL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            val isValid = name.isNotBlank() && (selectedChildIds.isNotEmpty() || policySubId.isNotBlank())
            Button(
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
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加链式代理" else "编辑链式代理") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("链式代理名称") },
                    placeholder = { Text("例如：国内中转 -> 香港落地") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "流量将按自上而下顺序依次穿透：第 1 跳为本机直连的前置入口节点，最后一跳为最终落地出口节点。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "代理链路 (${selectedHopIds.size} 跳)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = { showHopPicker = true },
                        enabled = eligibleNodes.isNotEmpty()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加一跳")
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
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
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
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
 * 通用协议节点编辑器（全面覆盖 VMess / VLESS / Shadowsocks / Trojan / Hysteria2 / WireGuard / MASQUE / SOCKS / HTTP）
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialNode == null) "添加 [${protocol.displayName}]"
                else "编辑 [${protocol.displayName}]"
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectableProtocols.forEach { p ->
                        FilterChip(
                            selected = protocol == p,
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
                            },
                            label = { Text(p.displayName) }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("节点备注名称（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("服务器地址 (域名或 IP)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = portStr,
                    onValueChange = { portStr = it.filter { ch -> ch.isDigit() } },
                    label = { Text("端口") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                when (protocol) {
                    ProtocolType.VLESS, ProtocolType.VMESS, ProtocolType.TROJAN -> {
                        OutlinedTextField(
                            value = credential,
                            onValueChange = { credential = it },
                            label = { Text(if (protocol == ProtocolType.TROJAN) "密码" else "UUID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (protocol == ProtocolType.VLESS) {
                            OutlinedTextField(
                                value = flow,
                                onValueChange = { flow = it },
                                label = { Text("流控 Flow（可选，如 xtls-rprx-vision）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (protocol == ProtocolType.VMESS) {
                            OutlinedTextField(
                                value = method,
                                onValueChange = { method = it },
                                label = { Text("加密方式 (auto / aes-128-gcm / chacha20-poly1305 / none)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Text("传输协议 (network)", style = MaterialTheme.typography.labelMedium)
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
                            ).forEach { net ->
                                FilterChip(
                                    selected = network == net,
                                    onClick = { network = net },
                                    label = { Text(net.value.uppercase()) }
                                )
                            }
                        }
                        if (network == TransportNetwork.WS ||
                            network == TransportNetwork.HTTPUPGRADE ||
                            network == TransportNetwork.XHTTP
                        ) {
                            OutlinedTextField(
                                value = host,
                                onValueChange = { host = it },
                                label = { Text("伪装域名 Host（可选）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = path,
                                onValueChange = { path = it },
                                label = { Text("路径 Path（如 /ws）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (network == TransportNetwork.XHTTP) {
                                OutlinedTextField(
                                    value = xhttpMode,
                                    onValueChange = { xhttpMode = it },
                                    label = { Text("XHTTP 模式（auto / packet-up / stream-up / stream-one）") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else if (network == TransportNetwork.GRPC) {
                            OutlinedTextField(
                                value = grpcServiceName,
                                onValueChange = { grpcServiceName = it },
                                label = { Text("gRPC serviceName") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Text("传输安全 (security)", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SecurityMode.entries.forEach { sec ->
                                FilterChip(
                                    selected = security == sec,
                                    onClick = { security = sec },
                                    label = { Text(sec.value.uppercase()) }
                                )
                            }
                        }
                        if (security != SecurityMode.NONE) {
                            OutlinedTextField(
                                value = sni,
                                onValueChange = { sni = it },
                                label = { Text("SNI / ServerName（可选）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = fingerprint,
                                onValueChange = { fingerprint = it },
                                label = { Text("uTLS 指纹（可选，如 chrome / ios）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (security == SecurityMode.TLS) {
                            OutlinedTextField(
                                value = alpn,
                                onValueChange = { alpn = it },
                                label = { Text("ALPN（可选，如 h2,http/1.1）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            InsecureCertToggleRow(
                                checked = allowInsecure,
                                onCheckedChange = { allowInsecure = it }
                            )
                        } else if (security == SecurityMode.REALITY) {
                            OutlinedTextField(
                                value = realityPbk,
                                onValueChange = { realityPbk = it },
                                label = { Text("Reality PublicKey (pbk)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = realitySid,
                                onValueChange = { realitySid = it },
                                label = { Text("Reality ShortId (sid)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = realitySpx,
                                onValueChange = { realitySpx = it },
                                label = { Text("Reality SpiderX（可选）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    ProtocolType.SHADOWSOCKS -> {
                        OutlinedTextField(
                            value = method,
                            onValueChange = { method = it },
                            label = { Text("加密方式 (如 aes-256-gcm / 2022-blake3-aes-128-gcm)") },
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
                            ).forEach { cipher ->
                                FilterChip(
                                    selected = method == cipher,
                                    onClick = { method = cipher },
                                    label = { Text(cipher) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = credential,
                            onValueChange = { credential = it },
                            label = { Text("密码") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ProtocolType.HYSTERIA2 -> {
                        OutlinedTextField(
                            value = credential,
                            onValueChange = { credential = it },
                            label = { Text("认证密码 (auth)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = sni,
                            onValueChange = { sni = it },
                            label = { Text("SNI / 伪装域名（可选）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = hy2ObfsPass,
                            onValueChange = { hy2ObfsPass = it },
                            label = { Text("Salamander 混淆密码（可选）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = hy2PortHopping,
                            onValueChange = { hy2PortHopping = it },
                            label = { Text("端口跳跃范围 mport（可选，如 20000-30000）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = hy2UpStr,
                                onValueChange = { hy2UpStr = it.filter(Char::isDigit) },
                                label = { Text("上行 Mbps") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = hy2DownStr,
                                onValueChange = { hy2DownStr = it.filter(Char::isDigit) },
                                label = { Text("下行 Mbps") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = pinSha256,
                            onValueChange = { pinSha256 = it },
                            label = { Text("证书指纹 PinSHA256（可选）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        InsecureCertToggleRow(
                            checked = allowInsecure,
                            onCheckedChange = { allowInsecure = it }
                        )
                    }

                    ProtocolType.WIREGUARD -> {
                        OutlinedTextField(
                            value = wgPrivateKey,
                            onValueChange = { wgPrivateKey = it },
                            label = { Text("本机私钥 PrivateKey (Base64)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = wgPublicKey,
                            onValueChange = { wgPublicKey = it },
                            label = { Text("对端公钥 Peer PublicKey (Base64)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = wgPresharedKey,
                            onValueChange = { wgPresharedKey = it },
                            label = { Text("预共享密钥 PreSharedKey（可选）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = wgLocalAddress,
                            onValueChange = { wgLocalAddress = it },
                            label = { Text("本地接口地址 CIDR（如 10.0.0.2/32）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = wgMtuStr,
                                onValueChange = { wgMtuStr = it.filter(Char::isDigit) },
                                label = { Text("MTU") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = wgReserved,
                                onValueChange = { wgReserved = it },
                                label = { Text("Reserved（可选 0,0,0）") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    ProtocolType.MASQUE -> {
                        OutlinedTextField(
                            value = sni,
                            onValueChange = { sni = it },
                            label = { Text("SNI / 证书域名（如 consumer-masque.cloudflareclient.com）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Host 请求头（可选，如 cloudflareaccess.com）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = masquePath,
                            onValueChange = { masquePath = it },
                            label = { Text("Path（可选，默认 /.well-known/masque/ip/*/*/，WARP 用 /）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = masqueRemoteDns,
                            onValueChange = { masqueRemoteDns = it },
                            label = { Text("远端 DNS IP（可选，逗号分隔）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { useMasqueWarp = !useMasqueWarp },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("启用 WARP-over-MASQUE 密钥参数", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = useMasqueWarp, onCheckedChange = { useMasqueWarp = it })
                        }
                        if (useMasqueWarp) {
                            OutlinedTextField(
                                value = masqueWarpPk,
                                onValueChange = { masqueWarpPk = it },
                                label = { Text("WARP PrivateKey (ECDSA P-256 Base64/PEM)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = masqueWarpPub,
                                onValueChange = { masqueWarpPub = it },
                                label = { Text("WARP PublicKey (ECDSA P-256 Base64/PEM)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = masqueWarpAddr,
                                onValueChange = { masqueWarpAddr = it },
                                label = { Text("WARP 虚拟地址 CIDR（逗号分隔）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("Basic 用户名（可选）") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = credential,
                                    onValueChange = { credential = it },
                                    label = { Text("Basic 密码（可选）") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = masqueHeaders,
                                onValueChange = { masqueHeaders = it },
                                label = { Text("自定义请求头（可选，每行 Key: Value）") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = pinSha256,
                            onValueChange = { pinSha256 = it },
                            label = { Text("证书指纹 PinSHA256（可选）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        InsecureCertToggleRow(
                            checked = allowInsecure,
                            onCheckedChange = { allowInsecure = it }
                        )
                    }

                    ProtocolType.SOCKS, ProtocolType.HTTP -> {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("用户名（可选）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = credential,
                            onValueChange = { credential = it },
                            label = { Text("密码（可选）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (protocol == ProtocolType.HTTP) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        security = if (security == SecurityMode.TLS) SecurityMode.NONE else SecurityMode.TLS
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("启用 HTTPS (TLS)", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = security == SecurityMode.TLS,
                                    onCheckedChange = {
                                        security = if (it) SecurityMode.TLS else SecurityMode.NONE
                                    }
                                )
                            }
                            if (security == SecurityMode.TLS) {
                                OutlinedTextField(
                                    value = sni,
                                    onValueChange = { sni = it },
                                    label = { Text("SNI（可选）") },
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
        confirmButton = {
            val validPort = portStr.toIntOrNull()?.takeIf { it in 1..65535 }
                ?: defaultPortForProtocol(protocol)
            val isValid = server.isNotBlank() && when (protocol) {
                ProtocolType.WIREGUARD -> wgPrivateKey.isNotBlank() && wgPublicKey.isNotBlank()
                ProtocolType.VLESS, ProtocolType.VMESS -> credential.isNotBlank()
                else -> true
            }
            Button(
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
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
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
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text("允许不安全 TLS 证书 (allowInsecure)", style = MaterialTheme.typography.bodySmall)
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
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = labelPrefix(idx, childIds.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = target?.name ?: "已失效节点 ($id)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (target != null) {
                            Text(
                                text = target.badgeText(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (idx > 0) {
                                val item = childIds.removeAt(idx)
                                childIds.add(idx - 1, item)
                            }
                        },
                        enabled = idx > 0,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "上移", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = {
                            if (idx < childIds.lastIndex) {
                                val item = childIds.removeAt(idx)
                                childIds.add(idx + 1, item)
                            }
                        },
                        enabled = idx < childIds.lastIndex,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "下移", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { childIds.removeAt(idx) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = "移除", modifier = Modifier.size(16.dp))
                    }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                candidates.forEach { candidate ->
                    val isChecked = !allowDuplicates && candidate.id in pickedOrder
                    Surface(
                        tonalElevation = if (isChecked) 3.dp else 1.dp,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
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
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (candidate.id !in pickedOrder) pickedOrder.add(candidate.id)
                                        } else {
                                            pickedOrder.remove(candidate.id)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = candidate.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${candidate.badgeText()} · ${candidate.displayAddressSummary()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (allowDuplicates) {
                                Icon(Icons.Rounded.Add, contentDescription = "添加", modifier = Modifier.size(18.dp))
                            } else if (isChecked) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!allowDuplicates) {
                Button(onClick = { onConfirm(pickedOrder.toList()) }) {
                    Text("确定 (${pickedOrder.size})")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
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
