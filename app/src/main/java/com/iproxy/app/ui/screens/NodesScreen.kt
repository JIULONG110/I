package com.iproxy.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iproxy.app.core.util.FormatUtils
import com.iproxy.app.data.model.ProtocolType
import com.iproxy.app.data.model.ServerNode
import com.iproxy.app.data.model.Subscription
import com.iproxy.app.ui.QrCodeHelper
import com.iproxy.app.ui.components.LiquidGlassCard
import com.iproxy.app.ui.components.ProtoTextMuted
import com.iproxy.app.ui.components.ProtoTextPrimary
import com.iproxy.app.ui.components.ProtoTextSubtle
import com.iproxy.app.ui.components.prototypePrimaryButtonColors
import com.iproxy.app.ui.components.prototypeTextFieldColors
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
 * 节点与订阅管理页面：对齐暗黑蓝宝石玻璃材质、间距与列表选态，保留全部节点及订阅操作
 */
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
    var activeGroupFilter by remember { mutableStateOf<String?>("ALL") }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "节点",
                    fontSize = 24.sp,
                    lineHeight = 29.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProtoTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "共 ${nodes.size} 个线路 · 点击即可切换当前节点",
                    fontSize = 13.sp,
                    color = ProtoTextMuted
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockActionIconButton(
                    icon = Icons.Rounded.Speed,
                    contentDescription = "全部测速",
                    enabled = !isTestingLatency,
                    onClick = onMeasureAll
                )
                DockActionIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "更新订阅",
                    enabled = !isRefreshingSubs,
                    onClick = onRefreshAllSubscriptions
                )
                DockActionIconButton(
                    icon = Icons.AutoMirrored.Rounded.Sort,
                    contentDescription = "按延迟排序",
                    onClick = onSortByLatency
                )
                DockActionIconButton(
                    icon = Icons.Rounded.CleaningServices,
                    contentDescription = "清理超时节点",
                    onClick = onClearUnreachable
                )
                Box {
                    DockActionIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "添加节点或订阅",
                        onClick = { showAddMenu = true }
                    )
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false },
                        containerColor = Color(0xFF0E192A),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color(0x66709ACA)),
                        modifier = Modifier.heightIn(max = 460.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("从剪贴板导入分享链接", color = ProtoTextPrimary) },
                            leadingIcon = { Icon(Icons.Rounded.ContentPasteGo, null, tint = Color(0xFF59BAFF)) },
                            onClick = {
                                showAddMenu = false
                                onImportClipboard()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("扫描相机二维码", color = ProtoTextPrimary) },
                            leadingIcon = { Icon(Icons.Rounded.QrCodeScanner, null, tint = Color(0xFF59BAFF)) },
                            onClick = {
                                showAddMenu = false
                                onOpenQrScan()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("扫描图片中的二维码", color = ProtoTextPrimary) },
                            leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null, tint = Color(0xFF59BAFF)) },
                            onClick = {
                                showAddMenu = false
                                qrImagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("添加订阅链接", color = ProtoTextPrimary) },
                            leadingIcon = { Icon(Icons.Rounded.AddLink, null, tint = Color(0xFF59BAFF)) },
                            onClick = {
                                showAddMenu = false
                                showSubDialog = true
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = Color(0x335D88B9)
                        )

                        DropdownMenuItem(
                            text = { Text("添加自定义配置", color = ProtoTextPrimary) },
                            leadingIcon = { Icon(Icons.Rounded.Code, null, tint = Color(0xFF79D1FF)) },
                            onClick = {
                                showAddMenu = false
                                activeEditor = ActiveNodeEditor.CustomJson(isOutbound = false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("添加策略组", color = ProtoTextPrimary) },
                            leadingIcon = { Icon(Icons.Rounded.Tune, null, tint = Color(0xFF79D1FF)) },
                            onClick = {
                                showAddMenu = false
                                activeEditor = ActiveNodeEditor.PolicyGroup()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("添加链式代理", color = ProtoTextPrimary) },
                            leadingIcon = { Icon(Icons.Rounded.Route, null, tint = Color(0xFF79D1FF)) },
                            onClick = {
                                showAddMenu = false
                                activeEditor = ActiveNodeEditor.ProxyChain()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("添加自定义出站", color = ProtoTextPrimary) },
                            leadingIcon = { Icon(Icons.Rounded.Dns, null, tint = Color(0xFF79D1FF)) },
                            onClick = {
                                showAddMenu = false
                                activeEditor = ActiveNodeEditor.CustomJson(isOutbound = true)
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = Color(0x335D88B9)
                        )

                        menuProtocolEntries.forEach { proto ->
                            DropdownMenuItem(
                                text = { Text("添加 [${proto.displayName}]", color = ProtoTextPrimary) },
                                onClick = {
                                    showAddMenu = false
                                    activeEditor = ActiveNodeEditor.StandardProtocol(protocol = proto)
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isTestingLatency || isRefreshingSubs) {
            LinearProgressIndicator(
                color = Color(0xFF53CAFF),
                trackColor = Color(0xFF142338),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索节点名称、地址或协议...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF79D1FF)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = prototypeTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassFilterPill(
                label = "全部 (${nodes.size})",
                selected = activeGroupFilter == "ALL",
                onClick = { activeGroupFilter = "ALL" }
            )
            GlassFilterPill(
                label = "手动导入",
                selected = activeGroupFilter == "LOCAL",
                onClick = { activeGroupFilter = "LOCAL" }
            )
            subscriptions.forEach { sub ->
                GlassFilterPill(
                    label = "${sub.name} (${sub.nodeCount})",
                    selected = activeGroupFilter == sub.id,
                    onClick = { activeGroupFilter = sub.id }
                )
            }
        }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
                    .padding(bottom = 112.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无匹配节点，点击右上角「+」添加或导入",
                    fontSize = 13.sp,
                    color = ProtoTextSubtle
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 6.dp, bottom = 126.dp),
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
            containerColor = Color(0xFF0E192A),
            titleContentColor = ProtoTextPrimary,
            textContentColor = ProtoTextPrimary,
            onDismissRequest = { qrPreviewNode = null },
            title = { Text(node.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "节点二维码",
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = shareLink,
                        style = MaterialTheme.typography.bodySmall,
                        color = ProtoTextMuted,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = prototypePrimaryButtonColors(),
                    onClick = {
                        onCopyText(shareLink, "已复制节点分享内容")
                        qrPreviewNode = null
                    }
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制内容")
                }
            },
            dismissButton = {
                TextButton(onClick = { qrPreviewNode = null }) {
                    Text("关闭", color = ProtoTextMuted)
                }
            }
        )
    }
}

@Composable
private fun DockActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val bgBrush = Brush.linearGradient(listOf(Color(0xCC1779E6), Color(0xE0074097)))
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(bgBrush, shape)
            .border(
                width = 1.dp,
                color = Color(0xD179D1FF),
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun GlassFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val bgBrush = if (selected) {
        Brush.linearGradient(listOf(Color(0x9E1779E6), Color(0xC2074097)))
    } else {
        Brush.linearGradient(listOf(Color(0xF0233043), Color(0xFA111A29)))
    }
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bgBrush, shape)
            .border(
                width = 1.dp,
                color = if (selected) Color(0xD179D1FF) else Color(0x52709ACA),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Color.White else Color(0xFFC0D4EA),
            maxLines = 1
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
            .padding(vertical = 6.dp),
        cornerRadius = 18.dp,
        elevated = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(sub.name, fontWeight = FontWeight.SemiBold, color = ProtoTextPrimary)
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
                    color = ProtoTextMuted
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
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新订阅", tint = Color(0xFFB7D4F2))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "删除订阅", tint = Color(0xFFB7D4F2))
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
        cornerRadius = 21.dp,
        elevated = isSelected,
        accentBorder = if (isSelected) Color(0xE646B5FF) else null,
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(21.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFF168BFF) else Color(0xDB091A30))
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color(0xFF78D8FF) else Color(0x9E6BA6E0),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "已选中",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProtoTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x474574A4))
                            .border(1.dp, Color(0x386BA3D7), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = node.badgeText(),
                            fontSize = 11.sp,
                            color = Color(0xFFB6C6D9)
                        )
                    }
                    Text(
                        text = node.displayAddressSummary(),
                        fontSize = 11.sp,
                        color = Color(0xFF9EAFC3),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    LatencyBadge(latencyMs = node.latencyMs)
                }
            }
            IconButton(onClick = onMeasure, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Speed, contentDescription = "测速", tint = Color(0xFFB7D4F2), modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = onShareQr, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.QrCode2, contentDescription = "分享 / 二维码", tint = Color(0xFFB7D4F2), modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Edit, contentDescription = "编辑", tint = Color(0xFFB7D4F2), modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = Color(0xFFB7D4F2), modifier = Modifier.size(17.dp))
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
        containerColor = Color(0xFF0E192A),
        titleContentColor = ProtoTextPrimary,
        textContentColor = ProtoTextPrimary,
        onDismissRequest = onDismiss,
        title = { Text("添加订阅链接") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("订阅备注（可选）") },
                    singleLine = true,
                    colors = prototypeTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("订阅 URL (http/https)") },
                    placeholder = { Text("https://example.com/sub?token=...") },
                    minLines = 2,
                    maxLines = 4,
                    colors = prototypeTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { autoUpdate = !autoUpdate }
                ) {
                    Checkbox(checked = autoUpdate, onCheckedChange = { autoUpdate = it })
                    Text("开启后台定时自动更新订阅", color = ProtoTextPrimary)
                }
            }
        },
        confirmButton = {
            Button(
                colors = prototypePrimaryButtonColors(),
                onClick = { onConfirm(name, url, autoUpdate) },
                enabled = url.trim().startsWith("http", ignoreCase = true)
            ) {
                Text("拉取并保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = ProtoTextMuted)
            }
        }
    )
}
