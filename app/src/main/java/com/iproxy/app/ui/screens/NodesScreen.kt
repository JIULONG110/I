package com.iproxy.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPasteGo
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iproxy.app.core.util.FormatUtils
import com.iproxy.app.data.model.ProtocolType
import com.iproxy.app.data.model.ServerNode
import com.iproxy.app.data.model.Subscription
import com.iproxy.app.ui.QrCodeHelper
import com.iproxy.app.ui.components.LiquidGlassCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface ActiveNodeEditor {
    data class CustomJson(val isOutbound: Boolean, val node: ServerNode? = null) : ActiveNodeEditor
    data class PolicyGroup(val node: ServerNode? = null) : ActiveNodeEditor
    data class ProxyChain(val node: ServerNode? = null) : ActiveNodeEditor
    data class StandardProtocol(val protocol: ProtocolType, val node: ServerNode? = null) : ActiveNodeEditor
}

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
    onImportRawText: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    onOpenQrScan: () -> Unit,
    onAddSubscription: (String, String, Boolean) -> Unit,
    onRefreshSubscription: (String) -> Unit,
    onRefreshAllSubscriptions: () -> Unit,
    onDeleteSubscription: (String) -> Unit,
    onExportShareLink: (ServerNode) -> String,
    onCopyText: (String, String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var activeGroupFilter by remember { mutableStateOf<String?>("ALL") } // "ALL", "LOCAL", or sub.id
    var showAddMenu by remember { mutableStateOf(false) }
    var showSubDialog by remember(showAddSubDialogInitial) { mutableStateOf(showAddSubDialogInitial) }
    var activeEditor by remember { mutableStateOf<ActiveNodeEditor?>(null) }
    var qrPreviewNode by remember { mutableStateOf<ServerNode?>(null) }

    val qrImagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        QrCodeHelper.decodeFromUri(context, uri)
            .onSuccess { onImportRawText(it) }
            .onFailure { onShowMessage(it.message ?: "读取图片失败") }
    }

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

    val menuProtocolEntries = remember {
        listOf(
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
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("节点与订阅 (${nodes.size})") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false },
                    modifier = Modifier.heightIn(max = 480.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("从剪贴板导入分享链接") },
                        leadingIcon = { Icon(Icons.Rounded.ContentPasteGo, null) },
                        onClick = {
                            showAddMenu = false
                            onImportClipboard()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("扫描相机二维码") },
                        leadingIcon = { Icon(Icons.Rounded.QrCodeScanner, null) },
                        onClick = {
                            showAddMenu = false
                            onOpenQrScan()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("扫描图片中的二维码") },
                        leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null) },
                        onClick = {
                            showAddMenu = false
                            qrImagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    DropdownMenuItem(
                        text = { Text("添加自定义配置") },
                        leadingIcon = { Icon(Icons.Rounded.Code, null) },
                        onClick = {
                            showAddMenu = false
                            activeEditor = ActiveNodeEditor.CustomJson(isOutbound = false)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("添加策略组") },
                        leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                        onClick = {
                            showAddMenu = false
                            activeEditor = ActiveNodeEditor.PolicyGroup()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("添加链式代理") },
                        leadingIcon = { Icon(Icons.Rounded.Route, null) },
                        onClick = {
                            showAddMenu = false
                            activeEditor = ActiveNodeEditor.ProxyChain()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("添加自定义出站") },
                        leadingIcon = { Icon(Icons.Rounded.Dns, null) },
                        onClick = {
                            showAddMenu = false
                            activeEditor = ActiveNodeEditor.CustomJson(isOutbound = true)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    menuProtocolEntries.forEach { proto ->
                        DropdownMenuItem(
                            text = { Text("添加 [${proto.displayName}]") },
                            onClick = {
                                showAddMenu = false
                                activeEditor = ActiveNodeEditor.StandardProtocol(protocol = proto)
                            }
                        )
                    }
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
                        text = "暂无匹配节点，点击右下角「+」添加或导入",
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
                                activeEditor = when (node.protocol) {
                                    ProtocolType.CUSTOM_JSON -> ActiveNodeEditor.CustomJson(isOutbound = false, node = node)
                                    ProtocolType.CUSTOM_OUTBOUND -> ActiveNodeEditor.CustomJson(isOutbound = true, node = node)
                                    ProtocolType.POLICY_GROUP -> ActiveNodeEditor.PolicyGroup(node = node)
                                    ProtocolType.PROXY_CHAIN -> ActiveNodeEditor.ProxyChain(node = node)
                                    else -> ActiveNodeEditor.StandardProtocol(protocol = node.protocol, node = node)
                                }
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

    when (val editor = activeEditor) {
        is ActiveNodeEditor.CustomJson -> {
            CustomJsonEditorDialog(
                isOutbound = editor.isOutbound,
                initial = editor.node,
                onDismiss = { activeEditor = null },
                onSave = { node ->
                    activeEditor = null
                    onSaveNode(node)
                }
            )
        }
        is ActiveNodeEditor.PolicyGroup -> {
            PolicyGroupEditorDialog(
                initial = editor.node,
                allNodes = nodes,
                subscriptions = subscriptions,
                onDismiss = { activeEditor = null },
                onSave = { node ->
                    activeEditor = null
                    onSaveNode(node)
                }
            )
        }
        is ActiveNodeEditor.ProxyChain -> {
            ProxyChainEditorDialog(
                initial = editor.node,
                allNodes = nodes,
                onDismiss = { activeEditor = null },
                onSave = { node ->
                    activeEditor = null
                    onSaveNode(node)
                }
            )
        }
        is ActiveNodeEditor.StandardProtocol -> {
            ProtocolNodeEditorDialog(
                initialProtocol = editor.protocol,
                initialNode = editor.node,
                onDismiss = { activeEditor = null },
                onSave = { node ->
                    activeEditor = null
                    onSaveNode(node)
                }
            )
        }
        null -> Unit
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
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onCopyText(shareLink, "已复制节点分享内容")
                    qrPreviewNode = null
                }) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制内容")
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
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        cornerRadius = 18.dp,
        elevated = true
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
    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        elevated = isSelected,
        accentBorder = if (isSelected) MaterialTheme.colorScheme.primary else null,
        onClick = onSelect
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
                        text = node.displayAddressSummary(),
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
                Icon(Icons.Rounded.QrCode2, contentDescription = "分享 / 二维码", modifier = Modifier.size(18.dp))
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
