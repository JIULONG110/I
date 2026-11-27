package com.iproxy.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPasteGo
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iproxy.app.core.util.FormatUtils
import com.iproxy.app.data.model.ProtocolType
import com.iproxy.app.data.model.SecurityMode
import com.iproxy.app.data.model.ServerNode
import com.iproxy.app.data.model.Subscription
import com.iproxy.app.data.model.TransportNetwork
import com.iproxy.app.ui.QrCodeHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 节点与订阅管理页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodesScreen(
    nodes: List<ServerNode>,
    subscriptions: List<Subscription>,
    selectedNodeId: String?,
    isTestingLatency: Boolean,
    isRefreshingSubs: Boolean,
    showAddSubDialogInitial: Boolean,
    onDismissAddSubInitial: () -> Unit,
    onSelectNode: (String) -> Unit,
    onSaveNode: (ServerNode) -> Unit,
    onDeleteNode: (String) -> Unit,
    onMeasureSingle: (String) -> Unit,
    onMeasureAll: () -> Unit,
    onSortByLatency: () -> Unit,
    onClearUnreachable: () -> Unit,
    onImportClipboard: () -> Unit,
    onOpenQrScan: () -> Unit,
    onAddSubscription: (String, String, Boolean) -> Unit,
    onRefreshSubscription: (String) -> Unit,
    onRefreshAllSubscriptions: () -> Unit,
    onDeleteSubscription: (String) -> Unit,
    onExportShareLink: (ServerNode) -> String,
    onCopyText: (String, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var activeGroupFilter by remember { mutableStateOf<String?>("ALL") } // "ALL", "LOCAL", or sub.id
    var showAddMenu by remember { mutableStateOf(false) }
    var showSubDialog by remember(showAddSubDialogInitial) { mutableStateOf(showAddSubDialogInitial) }
    var editingNode by remember { mutableStateOf<ServerNode?>(null) }
    var showEditNodeDialog by remember { mutableStateOf(false) }
    var qrPreviewNode by remember { mutableStateOf<ServerNode?>(null) }

    val filteredNodes = remember(nodes, searchQuery, activeGroupFilter) {
        nodes.filter { node ->
            val matchGroup = when (activeGroupFilter) {
                "ALL", null -> true
                "LOCAL" -> node.subscriptionId == null
                else -> node.subscriptionId == activeGroupFilter
            }
            val matchSearch = searchQuery.isBlank() ||
                node.name.contains(searchQuery, ignoreCase = true) ||
                node.server.contains(searchQuery, ignoreCase = true) ||
                node.protocol.displayName.contains(searchQuery, ignoreCase = true)
            matchGroup && matchSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("节点与订阅 (${nodes.size})") },
                actions = {
                    IconButton(onClick = onMeasureAll, enabled = !isTestingLatency) {
                        Icon(Icons.Rounded.Speed, contentDescription = "全部测速")
                    }
                    IconButton(onClick = onRefreshAllSubscriptions, enabled = !isRefreshingSubs) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "更新订阅")
                    }
                    IconButton(onClick = onSortByLatency) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "按延迟排序")
                    }
                    IconButton(onClick = onClearUnreachable) {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = "清理超时节点")
                    }
                }
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { showAddMenu = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "添加节点或订阅")
                }
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("从剪贴板导入节点 / 订阅") },
                        leadingIcon = { Icon(Icons.Rounded.ContentPasteGo, null) },
                        onClick = {
                            showAddMenu = false
                            onImportClipboard()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("扫描二维码导入") },
                        leadingIcon = { Icon(Icons.Rounded.QrCodeScanner, null) },
                        onClick = {
                            showAddMenu = false
                            onOpenQrScan()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("添加订阅链接") },
                        leadingIcon = { Icon(Icons.Rounded.AddLink, null) },
                        onClick = {
                            showAddMenu = false
                            showSubDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("手动创建节点") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = {
                            showAddMenu = false
                            editingNode = null
                            showEditNodeDialog = true
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isTestingLatency || isRefreshingSubs) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索节点名称、地址或协议...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 分组筛选栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeGroupFilter == "ALL",
                    onClick = { activeGroupFilter = "ALL" },
                    label = { Text("全部 (${nodes.size})") }
                )
                FilterChip(
                    selected = activeGroupFilter == "LOCAL",
                    onClick = { activeGroupFilter = "LOCAL" },
                    label = { Text("手动导入") }
                )
                subscriptions.forEach { sub ->
                    FilterChip(
                        selected = activeGroupFilter == sub.id,
                        onClick = { activeGroupFilter = sub.id },
                        label = { Text("${sub.name} (${sub.nodeCount})") }
                    )
                }
            }

            // 若当前选中了某个订阅，展示该订阅流量与操作卡
            val currentSub = subscriptions.firstOrNull { it.id == activeGroupFilter }
            if (currentSub != null) {
                SubscriptionInfoBanner(
                    sub = currentSub,
                    onRefresh = { onRefreshSubscription(currentSub.id) },
                    onDelete = {
                        onDeleteSubscription(currentSub.id)
                        activeGroupFilter = "ALL"
                    }
                )
            }

            if (filteredNodes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无匹配节点，点击右下角「+」导入",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredNodes, key = { it.id }) { node ->
                        val isSelected = node.id == selectedNodeId
                        NodeItemCard(
                            node = node,
                            isSelected = isSelected,
                            onSelect = { onSelectNode(node.id) },
                            onMeasure = { onMeasureSingle(node.id) },
                            onEdit = {
                                editingNode = node
                                showEditNodeDialog = true
                            },
                            onShareQr = { qrPreviewNode = node },
                            onDelete = { onDeleteNode(node.id) }
                        )
                    }
                }
            }
        }
    }

    if (showSubDialog) {
        AddSubscriptionDialog(
            onDismiss = {
                showSubDialog = false
                onDismissAddSubInitial()
            },
            onConfirm = { name, url, autoUpdate ->
                showSubDialog = false
                onDismissAddSubInitial()
                onAddSubscription(name, url, autoUpdate)
            }
        )
    }

    if (showEditNodeDialog) {
        NodeEditorDialog(
            initial = editingNode,
            onDismiss = { showEditNodeDialog = false },
            onSave = { node ->
                showEditNodeDialog = false
                onSaveNode(node)
            }
        )
    }

    qrPreviewNode?.let { node ->
        val shareLink = remember(node) { onExportShareLink(node) }
        val qrBitmap = remember(shareLink) { QrCodeHelper.generateQrBitmap(shareLink) }
        AlertDialog(
            onDismissRequest = { qrPreviewNode = null },
            title = { Text(node.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "节点二维码",
                            modifier = Modifier.size(220.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = shareLink,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onCopyText(shareLink, "已复制节点分享链接")
                    qrPreviewNode = null
                }) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制链接")
                }
            },
            dismissButton = {
                TextButton(onClick = { qrPreviewNode = null }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun SubscriptionInfoBanner(
    sub: Subscription,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(sub.name, fontWeight = FontWeight.SemiBold)
                    val trafficText = if (sub.totalBytes > 0) {
                        "已用 ${FormatUtils.formatBytes(sub.usedBytes.coerceAtLeast(0))} / 总计 ${FormatUtils.formatBytes(sub.totalBytes)}"
                    } else {
                        "共 ${sub.nodeCount} 个节点"
                    }
                    val expireText = if (sub.expireTimestampSec > 0) {
                        val df = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                        " · 到期 ${df.format(Date(sub.expireTimestampSec * 1000L))}"
                    } else ""
                    Text(
                        text = "$trafficText$expireText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!sub.lastError.isNullOrBlank()) {
                        Text(
                            text = "上次错误: ${sub.lastError}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Row {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新订阅")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, contentDescription = "删除订阅")
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeItemCard(
    node: ServerNode,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onMeasure: () -> Unit,
    onEdit: () -> Unit,
    onShareQr: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isSelected) BorderStroke(1.8.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = node.badgeText(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "${node.server}:${node.port}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    LatencyBadge(latencyMs = node.latencyMs)
                }
            }
            IconButton(onClick = onMeasure, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.Speed, contentDescription = "测速", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onShareQr, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.QrCode2, contentDescription = "二维码分享", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var autoUpdate by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加订阅链接") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("订阅备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("订阅 URL (http/https)") },
                    placeholder = { Text("https://example.com/sub?token=...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { autoUpdate = !autoUpdate }
                ) {
                    Checkbox(checked = autoUpdate, onCheckedChange = { autoUpdate = it })
                    Text("开启后台定时自动更新订阅")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, url, autoUpdate) },
                enabled = url.trim().startsWith("http", ignoreCase = true)
            ) {
                Text("拉取并保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun NodeEditorDialog(
    initial: ServerNode?,
    onDismiss: () -> Unit,
    onSave: (ServerNode) -> Unit
) {
    var protocol by remember { mutableStateOf(initial?.protocol ?: ProtocolType.VLESS) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var server by remember { mutableStateOf(initial?.server ?: "") }
    var portStr by remember { mutableStateOf(initial?.port?.toString() ?: "443") }
    var credential by remember { mutableStateOf(initial?.passwordOrUuid ?: "") }
    var sni by remember { mutableStateOf(initial?.sni ?: "") }
    var network by remember { mutableStateOf(initial?.network ?: TransportNetwork.TCP) }
    var security by remember { mutableStateOf(initial?.security ?: SecurityMode.TLS) }
    var path by remember { mutableStateOf(initial?.path ?: "") }
    var realityPbk by remember { mutableStateOf(initial?.realityPublicKey ?: "") }
    var realitySid by remember { mutableStateOf(initial?.realityShortId ?: "") }
    var method by remember { mutableStateOf(initial?.method ?: "aes-128-gcm") }
    var customJson by remember { mutableStateOf(initial?.customRawJson ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "手动创建节点" else "编辑节点") },
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
                    listOf(
                        ProtocolType.VLESS,
                        ProtocolType.VMESS,
                        ProtocolType.TROJAN,
                        ProtocolType.SHADOWSOCKS,
                        ProtocolType.HYSTERIA2,
                        ProtocolType.SOCKS,
                        ProtocolType.CUSTOM_JSON
                    ).forEach { p ->
                        FilterChip(
                            selected = protocol == p,
                            onClick = { protocol = p },
                            label = { Text(p.displayName) }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("节点名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (protocol == ProtocolType.CUSTOM_JSON) {
                    OutlinedTextField(
                        value = customJson,
                        onValueChange = { customJson = it },
                        label = { Text("完整 Xray JSON 配置") },
                        minLines = 6,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
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
                    OutlinedTextField(
                        value = credential,
                        onValueChange = { credential = it },
                        label = {
                            Text(
                                when (protocol) {
                                    ProtocolType.VLESS, ProtocolType.VMESS -> "UUID"
                                    else -> "密码 / 认证密钥"
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (protocol == ProtocolType.SHADOWSOCKS) {
                        OutlinedTextField(
                            value = method,
                            onValueChange = { method = it },
                            label = { Text("加密方式 (如 aes-256-gcm / 2022-blake3-aes-128-gcm)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (protocol == ProtocolType.VLESS || protocol == ProtocolType.VMESS || protocol == ProtocolType.TROJAN) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(TransportNetwork.TCP, TransportNetwork.WS, TransportNetwork.GRPC, TransportNetwork.XHTTP).forEach { net ->
                                FilterChip(
                                    selected = network == net,
                                    onClick = { network = net },
                                    label = { Text(net.value.uppercase()) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SecurityMode.entries.forEach { sec ->
                                FilterChip(
                                    selected = security == sec,
                                    onClick = { security = sec },
                                    label = { Text(sec.value.uppercase()) }
                                )
                            }
                        }
                        if (network == TransportNetwork.WS || network == TransportNetwork.XHTTP) {
                            OutlinedTextField(
                                value = path,
                                onValueChange = { path = it },
                                label = { Text("路径 (如 /ws)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    OutlinedTextField(
                        value = sni,
                        onValueChange = { sni = it },
                        label = { Text("SNI / 伪装域名（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (security == SecurityMode.REALITY) {
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
                    }
                }
            }
        },
        confirmButton = {
            val validPort = portStr.toIntOrNull()?.takeIf { it in 1..65535 } ?: 443
            val isValid = if (protocol == ProtocolType.CUSTOM_JSON) {
                customJson.isNotBlank()
            } else {
                server.isNotBlank()
            }
            Button(
                enabled = isValid,
                onClick = {
                    val saved = (initial ?: ServerNode(
                        name = name.ifBlank { "$server:$validPort" },
                        protocol = protocol,
                        server = server.trim(),
                        port = validPort
                    )).copy(
                        name = name.ifBlank { "$server:$validPort" },
                        protocol = protocol,
                        server = if (protocol == ProtocolType.CUSTOM_JSON) "custom" else server.trim(),
                        port = validPort,
                        passwordOrUuid = credential.trim(),
                        method = method.trim(),
                        network = if (protocol == ProtocolType.HYSTERIA2) TransportNetwork.HYSTERIA else network,
                        security = if (protocol == ProtocolType.HYSTERIA2) SecurityMode.TLS else security,
                        sni = sni.trim(),
                        path = path.trim(),
                        realityPublicKey = realityPbk.trim(),
                        realityShortId = realitySid.trim(),
                        customRawJson = customJson.trim()
                    )
                    onSave(saved)
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
