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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.iproxy.app.core.util.FormatUtils
import com.iproxy.app.data.model.ProtocolType
import com.iproxy.app.data.model.ServerNode
import com.iproxy.app.data.model.Subscription
import com.iproxy.app.ui.QrCodeHelper
import com.iproxy.app.ui.components.BlueConstellationParticlesCanvas
import com.iproxy.app.ui.components.GlassActionButton
import com.iproxy.app.ui.components.GlassCheckbox
import com.iproxy.app.ui.components.GlassDialog
import com.iproxy.app.ui.components.GlassDivider
import com.iproxy.app.ui.components.GlassDropdownItem
import com.iproxy.app.ui.components.GlassDropdownMenu
import com.iproxy.app.ui.components.GlassEmptyStateCard
import com.iproxy.app.ui.components.GlassGhostButton
import com.iproxy.app.ui.components.GlassLoadingBar
import com.iproxy.app.ui.components.GlassSecondaryButton
import com.iproxy.app.ui.components.GlassSelectableChip
import com.iproxy.app.ui.components.GlassSmallIconButton
import com.iproxy.app.ui.components.GlassTextField
import com.iproxy.app.ui.components.LiquidGlassCard
import com.iproxy.app.ui.components.ProtoTextMuted
import com.iproxy.app.ui.components.ProtoTextPrimary
import com.iproxy.app.ui.components.drawGlassCapsule3D
import com.iproxy.app.ui.components.rememberReduceMotionState
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
 * 节点与订阅管理页面：全面采用图 1 同款 3D 蓝色实体玻璃材质与高速蓝色星链粒子，保持全部功能、尺寸与交互一致。
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
                    seedOffsetSec = 0.2f,
                    onClick = onMeasureAll
                )
                DockActionIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "更新订阅",
                    enabled = !isRefreshingSubs,
                    seedOffsetSec = 0.9f,
                    onClick = onRefreshAllSubscriptions
                )
                DockActionIconButton(
                    icon = Icons.AutoMirrored.Rounded.Sort,
                    contentDescription = "按延迟排序",
                    seedOffsetSec = 1.6f,
                    onClick = onSortByLatency
                )
                DockActionIconButton(
                    icon = Icons.Rounded.CleaningServices,
                    contentDescription = "清理超时节点",
                    seedOffsetSec = 2.3f,
                    onClick = onClearUnreachable
                )
                Box {
                    DockActionIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "添加节点或订阅",
                        seedOffsetSec = 3.0f,
                        onClick = { showAddMenu = true }
                    )
                    GlassDropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false }
                    ) {
                        GlassDropdownItem(
                            text = "从剪贴板导入分享链接",
                            leadingIcon = Icons.Rounded.ContentPasteGo,
                            onClick = {
                                showAddMenu = false
                                onImportClipboard()
                            }
                        )
                        GlassDropdownItem(
                            text = "扫描相机二维码",
                            leadingIcon = Icons.Rounded.QrCodeScanner,
                            onClick = {
                                showAddMenu = false
                                onOpenQrScan()
                            }
                        )
                        GlassDropdownItem(
                            text = "扫描图片中的二维码",
                            leadingIcon = Icons.Rounded.PhotoLibrary,
                            onClick = {
                                showAddMenu = false
                                qrImagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                        GlassDropdownItem(
                            text = "添加订阅链接",
                            leadingIcon = Icons.Rounded.AddLink,
                            onClick = {
                                showAddMenu = false
                                showSubDialog = true
                            }
                        )

                        GlassDivider(modifier = Modifier.padding(vertical = 4.dp))

                        GlassDropdownItem(
                            text = "添加自定义配置",
                            leadingIcon = Icons.Rounded.Code,
                            accentTint = Color(0xFF79D1FF),
                            onClick = {
                                showAddMenu = false
                                activeEditor = ActiveNodeEditor.CustomJson(isOutbound = false)
                            }
                        )
                        GlassDropdownItem(
                            text = "添加策略组",
                            leadingIcon = Icons.Rounded.Tune,
                            accentTint = Color(0xFF79D1FF),
                            onClick = {
                                showAddMenu = false
                                activeEditor = ActiveNodeEditor.PolicyGroup()
                            }
                        )
                        GlassDropdownItem(
                            text = "添加链式代理",
                            leadingIcon = Icons.Rounded.Route,
                            accentTint = Color(0xFF79D1FF),
                            onClick = {
                                showAddMenu = false
                                activeEditor = ActiveNodeEditor.ProxyChain()
                            }
                        )
                        GlassDropdownItem(
                            text = "添加自定义出站",
                            leadingIcon = Icons.Rounded.Dns,
                            accentTint = Color(0xFF79D1FF),
                            onClick = {
                                showAddMenu = false
                                activeEditor = ActiveNodeEditor.CustomJson(isOutbound = true)
                            }
                        )

                        GlassDivider(modifier = Modifier.padding(vertical = 4.dp))

                        menuProtocolEntries.forEach { proto ->
                            GlassDropdownItem(
                                text = "添加 [${proto.displayName}]",
                                badge = proto.scheme.uppercase(),
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
            GlassLoadingBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        GlassTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "搜索节点名称、地址或协议...",
            leadingIcon = Icons.Rounded.Search,
            singleLine = true,
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
            GlassSelectableChip(
                label = "全部 (${nodes.size})",
                selected = activeGroupFilter == "ALL",
                seedOffsetSec = 0.2f,
                onClick = { activeGroupFilter = "ALL" }
            )
            GlassSelectableChip(
                label = "手动导入",
                selected = activeGroupFilter == "LOCAL",
                seedOffsetSec = 1.0f,
                onClick = { activeGroupFilter = "LOCAL" }
            )
            subscriptions.forEachIndexed { idx, sub ->
                GlassSelectableChip(
                    label = "${sub.name} (${sub.nodeCount})",
                    selected = activeGroupFilter == sub.id,
                    seedOffsetSec = 1.8f + idx * 0.7f,
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
                GlassEmptyStateCard(
                    title = if (nodes.isEmpty()) "暂无节点配置" else "未找到匹配节点",
                    subtitle = if (nodes.isEmpty()) {
                        "可从剪贴板导入节点分享链接、扫描二维码，或添加订阅链接。"
                    } else {
                        "请调整搜索关键词或分组筛选条件。"
                    }
                ) {
                    if (nodes.isEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassSecondaryButton(onClick = onImportClipboard) {
                                Icon(Icons.Rounded.ContentPasteGo, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("剪贴板导入", fontSize = 12.sp)
                            }
                            GlassActionButton(onClick = { showSubDialog = true }) {
                                Icon(Icons.Rounded.AddLink, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("添加订阅", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 6.dp, bottom = 126.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(filteredNodes, key = { _, node -> node.id }) { idx, node ->
                    val isSelected = node.id == selectedNodeId
                    NodeItemCard(
                        node = node,
                        isSelected = isSelected,
                        seedOffsetSec = idx * 0.45f,
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
        GlassDialog(
            onDismissRequest = { qrPreviewNode = null },
            title = node.name,
            content = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (qrBitmap != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.2.dp, Color(0xBD78D5FF), RoundedCornerShape(16.dp))
                                .padding(6.dp)
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "节点二维码",
                                modifier = Modifier
                                    .size(216.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        particleCount = 14
                    ) {
                        Text(
                            text = shareLink,
                            style = MaterialTheme.typography.bodySmall,
                            color = ProtoTextMuted,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            buttons = {
                GlassGhostButton(onClick = { qrPreviewNode = null }) {
                    Text("关闭")
                }
                GlassActionButton(
                    onClick = {
                        onCopyText(shareLink, "已复制节点分享内容")
                        qrPreviewNode = null
                    }
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制内容", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

/**
 * 节点顶栏 5 枚蓝色玻璃功能按钮：保持原尺寸 (36dp)、圆角 (12dp)、间距 (6dp) 与统一高饱和蓝色立体质感，内嵌流动蓝色粒子。
 */
@Composable
private fun DockActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    seedOffsetSec: Float = 0f,
    onClick: () -> Unit
) {
    val reduceMotion = rememberReduceMotionState()
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
            .alpha(if (enabled) 1f else 0.6f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BlueConstellationParticlesCanvas(
            particleCount = 12,
            reduceMotion = reduceMotion,
            referenceWidthDp = 120f,
            linkMaxDistDp = 28f,
            timeOffsetSec = seedOffsetSec,
            intensity = 0.95f,
            radiusScale = 0.72f,
            modifier = Modifier.matchParentSize()
        )
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
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
        elevated = true,
        particleCount = 20,
        seedOffsetSec = 1.5f
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassSmallIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "刷新订阅",
                    onClick = onRefresh
                )
                GlassSmallIconButton(
                    icon = Icons.Rounded.Delete,
                    contentDescription = "删除订阅",
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun NodeItemCard(
    node: ServerNode,
    isSelected: Boolean,
    seedOffsetSec: Float,
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
        isActive = isSelected,
        accentBorder = if (isSelected) Color(0xE646B5FF) else null,
        particleCount = 22,
        seedOffsetSec = seedOffsetSec,
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
                    .size(22.dp)
                    .clip(CircleShape)
                    .drawBehind {
                        drawGlassCapsule3D(
                            cornerRadiusDp = 11.dp,
                            active = isSelected,
                            innerBevelInsetDp = 1.5.dp
                        )
                    },
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
                            .drawBehind {
                                drawGlassCapsule3D(
                                    cornerRadiusDp = 8.dp,
                                    active = false,
                                    innerBevelInsetDp = 1.dp
                                )
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = node.badgeText(),
                            fontSize = 11.sp,
                            color = Color(0xFFB6E1FF)
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
            Spacer(modifier = Modifier.width(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                GlassSmallIconButton(
                    icon = Icons.Rounded.Speed,
                    contentDescription = "测速",
                    sizeDp = 30.dp,
                    cornerRadius = 9.dp,
                    onClick = onMeasure
                )
                GlassSmallIconButton(
                    icon = Icons.Rounded.QrCode2,
                    contentDescription = "分享 / 二维码",
                    sizeDp = 30.dp,
                    cornerRadius = 9.dp,
                    onClick = onShareQr
                )
                GlassSmallIconButton(
                    icon = Icons.Rounded.Edit,
                    contentDescription = "编辑",
                    sizeDp = 30.dp,
                    cornerRadius = 9.dp,
                    onClick = onEdit
                )
                GlassSmallIconButton(
                    icon = Icons.Rounded.Delete,
                    contentDescription = "删除",
                    sizeDp = 30.dp,
                    cornerRadius = 9.dp,
                    onClick = onDelete
                )
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

    GlassDialog(
        onDismissRequest = onDismiss,
        title = "添加订阅链接",
        content = {
            GlassTextField(
                value = name,
                onValueChange = { name = it },
                label = "订阅备注（可选）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            GlassTextField(
                value = url,
                onValueChange = { url = it },
                label = "订阅 URL (http/https)",
                placeholder = "https://example.com/sub?token=...",
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { autoUpdate = !autoUpdate }
                    .padding(vertical = 4.dp)
            ) {
                GlassCheckbox(checked = autoUpdate, onCheckedChange = { autoUpdate = it })
                Spacer(modifier = Modifier.width(10.dp))
                Text("开启后台定时自动更新订阅", color = ProtoTextPrimary, fontSize = 13.sp)
            }
        },
        buttons = {
            GlassGhostButton(onClick = onDismiss) {
                Text("取消")
            }
            GlassActionButton(
                enabled = url.trim().startsWith("http", ignoreCase = true),
                onClick = { onConfirm(name, url, autoUpdate) }
            ) {
                Text("拉取并保存", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
