package com.iproxy.app.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iproxy.app.ui.screens.HomeScreen
import com.iproxy.app.ui.screens.NodesScreen
import com.iproxy.app.ui.screens.PerAppSelectorScreen
import com.iproxy.app.ui.screens.QrScanScreen
import com.iproxy.app.ui.screens.RulesAndLogsScreen
import com.iproxy.app.ui.screens.SettingsScreen
import com.iproxy.app.ui.theme.IProxyTheme
import kotlinx.coroutines.launch

/**
 * 「I」主界面 Activity
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IProxyAppRoot()
        }
    }
}

@Composable
fun IProxyAppRoot(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsState()
    val nodes by viewModel.nodes.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val selectedNode by viewModel.selectedNode.collectAsState()
    val selectedNodeId by viewModel.selectedNodeId.collectAsState()
    val runtimeState by viewModel.runtimeState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isTestingLatency by viewModel.isTestingLatency.collectAsState()
    val isRefreshingSubs by viewModel.isRefreshingSubs.collectAsState()
    val isUpdatingGeo by viewModel.isUpdatingGeo.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val isLoadingApps by viewModel.isLoadingApps.collectAsState()

    var currentTab by remember { mutableIntStateOf(0) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showPerAppSelector by remember { mutableStateOf(false) }
    var openSubDialogOnNodes by remember { mutableStateOf(false) }

    // Android 13+ 通知权限引导（用于展示实时网速通知栏）
    val notificationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // 系统 VPN 授权对话框启动器
    val vpnPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onVpnPermissionGranted()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("需要授予系统 VPN 权限才能建立加密通道")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.requestVpnPermissionEvent.collect {
            val intent = VpnService.prepare(context)
            if (intent != null) {
                vpnPermLauncher.launch(intent)
            } else {
                viewModel.onVpnPermissionGranted()
            }
        }
    }

    fun copyToClipboard(text: String, tip: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("I", text))
        scope.launch { snackbarHostState.showSnackbar(tip) }
    }

    fun importFromClipboard() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (text.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("剪贴板为空，请先复制节点链接或订阅地址") }
            return
        }
        viewModel.importFromText(text)
    }

    IProxyTheme(themeMode = settings.themeMode) {
        when {
            showQrScanner -> {
                QrScanScreen(
                    onScanned = { scanned ->
                        showQrScanner = false
                        viewModel.importFromText(scanned)
                        currentTab = 1
                    },
                    onScanError = { err ->
                        scope.launch { snackbarHostState.showSnackbar(err) }
                    },
                    onBack = { showQrScanner = false }
                )
            }
            showPerAppSelector -> {
                PerAppSelectorScreen(
                    apps = installedApps,
                    isLoading = isLoadingApps,
                    selectedPackages = settings.selectedAppPackages,
                    onLoadApps = { viewModel.loadInstalledAppsIfNeeded() },
                    onTogglePackage = { viewModel.toggleAppSelection(it) },
                    onBack = { showPerAppSelector = false }
                )
            }
            else -> {
                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentTab == 0,
                                onClick = { currentTab = 0 },
                                icon = { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null) },
                                label = { Text("连接") }
                            )
                            NavigationBarItem(
                                selected = currentTab == 1,
                                onClick = { currentTab = 1 },
                                icon = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                                label = { Text("节点") }
                            )
                            NavigationBarItem(
                                selected = currentTab == 2,
                                onClick = { currentTab = 2 },
                                icon = { Icon(Icons.Rounded.Route, contentDescription = null) },
                                label = { Text("分流") }
                            )
                            NavigationBarItem(
                                selected = currentTab == 3,
                                onClick = { currentTab = 3 },
                                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                                label = { Text("设置") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentTab) {
                            0 -> HomeScreen(
                                runtimeState = runtimeState,
                                selectedNode = selectedNode,
                                nodeCount = nodes.size,
                                settings = settings,
                                isTestingLatency = isTestingLatency,
                                onToggleConnect = { viewModel.toggleConnection() },
                                onSelectNodeClick = { currentTab = 1 },
                                onTestCurrentNode = { viewModel.measureSingleNode(it) },
                                onChangeRoutingMode = { viewModel.setRoutingMode(it) },
                                onImportClipboard = { importFromClipboard() },
                                onOpenQrScan = { showQrScanner = true },
                                onOpenAddSub = {
                                    openSubDialogOnNodes = true
                                    currentTab = 1
                                }
                            )
                            1 -> NodesScreen(
                                nodes = nodes,
                                subscriptions = subscriptions,
                                selectedNodeId = selectedNodeId,
                                isTestingLatency = isTestingLatency,
                                isRefreshingSubs = isRefreshingSubs,
                                showAddSubDialogInitial = openSubDialogOnNodes,
                                onDismissAddSubInitial = { openSubDialogOnNodes = false },
                                onSelectNode = { viewModel.selectNode(it) },
                                onSaveNode = { viewModel.saveNode(it) },
                                onDeleteNode = { viewModel.deleteNode(it) },
                                onMeasureSingle = { viewModel.measureSingleNode(it) },
                                onMeasureAll = { viewModel.measureAllNodes() },
                                onSortByLatency = { viewModel.sortByLatency() },
                                onClearUnreachable = { viewModel.clearUnreachableNodes() },
                                onImportClipboard = { importFromClipboard() },
                                onOpenQrScan = { showQrScanner = true },
                                onAddSubscription = { n, u, a -> viewModel.addSubscription(n, u, a) },
                                onRefreshSubscription = { viewModel.refreshSubscription(it) },
                                onRefreshAllSubscriptions = { viewModel.refreshAllSubscriptions() },
                                onDeleteSubscription = { viewModel.deleteSubscription(it) },
                                onExportShareLink = { viewModel.exportNodeShareLink(it) },
                                onCopyText = { text, tip -> copyToClipboard(text, tip) }
                            )
                            2 -> RulesAndLogsScreen(
                                settings = settings,
                                logs = logs,
                                isUpdatingGeo = isUpdatingGeo,
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onSetRoutingMode = { viewModel.setRoutingMode(it) },
                                onUpdateGeoDatabases = { viewModel.updateGeoDatabases() },
                                onOpenPerAppSelector = { showPerAppSelector = true },
                                onCopyLogs = {
                                    val txt = viewModel.exportLogsText()
                                    if (txt.isBlank()) {
                                        scope.launch { snackbarHostState.showSnackbar("当前暂无日志") }
                                    } else {
                                        copyToClipboard(txt, "已复制全部运行日志")
                                    }
                                },
                                onClearLogs = { viewModel.clearLogs() }
                            )
                            3 -> SettingsScreen(
                                settings = settings,
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onExportBackup = { viewModel.exportBackupJson() },
                                onImportBackup = { viewModel.importBackupJson(it) },
                                onCopyText = { text, tip -> copyToClipboard(text, tip) }
                            )
                        }
                    }
                }
            }
        }
    }
}
