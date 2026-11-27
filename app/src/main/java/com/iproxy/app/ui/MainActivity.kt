package com.iproxy.app.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iproxy.app.data.model.VpnStatus
import com.iproxy.app.ui.components.AppUpdateDialog
import com.iproxy.app.ui.components.IProxyAlertDialog
import com.iproxy.app.ui.components.LiquidBackdrop
import com.iproxy.app.ui.components.LiquidBottomNavBar
import com.iproxy.app.ui.components.SplashBrandOverlay
import com.iproxy.app.ui.screens.HomeScreen
import com.iproxy.app.ui.screens.NodesScreen
import com.iproxy.app.ui.screens.PerAppSelectorScreen
import com.iproxy.app.ui.screens.QrScanScreen
import com.iproxy.app.ui.screens.RulesAndLogsScreen
import com.iproxy.app.ui.screens.SettingsScreen
import com.iproxy.app.ui.theme.IProxyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 「I」主界面 Activity
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
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

    val availableUpdate by viewModel.availableUpdate.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsState()
    val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsState()
    val updateDownloadError by viewModel.updateDownloadError.collectAsState()
    val pendingInstallApk by viewModel.pendingInstallApk.collectAsState()
    val signatureMismatch by viewModel.signatureMismatch.collectAsState()

    var currentTab by remember { mutableIntStateOf(0) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showPerAppSelector by remember { mutableStateOf(false) }
    var openSubDialogOnNodes by remember { mutableStateOf(false) }
    var showSplashOverlay by remember { mutableStateOf(true) }

    var hasPromptedNotificationPerm by remember { mutableStateOf(false) }
    var showNotificationRationaleDialog by remember { mutableStateOf(false) }
    var pendingVpnConsentIntent by remember { mutableStateOf<Intent?>(null) }
    var showInstallPermissionRationaleDialog by remember { mutableStateOf(false) }

    // 简短黑曜石液态开屏展示，随后平滑进入主界面；期间发现更新会立即弹窗
    LaunchedEffect(Unit) {
        delay(650L)
        showSplashOverlay = false
    }

    // 系统 VPN 授权对话框启动器（官方 VpnService.prepare 确认流程）
    val vpnPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onVpnPermissionGranted()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("未授予系统 VPN 权限，暂未建立通道；节点与订阅等其余功能仍可正常使用")
            }
        }
    }

    // Android 13+ 通知权限引导：仅在用户主动发起连接且先弹窗说明后按需申请；无论授权与否均继续后续流程
    val notificationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar("未开启通知权限，状态栏将不显示实时网速通知，代理连接仍可继续")
            }
        }
        viewModel.startConnectionFlow()
    }

    // 未知来源安装权限返回后自动继续安装 APK
    val installPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
        ) {
            viewModel.retryInstallPendingApk()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("未开启应用内安装权限，您可继续使用当前版本或点击「浏览器下载」手动安装")
            }
        }
    }

    fun handleUserConnectToggle() {
        val status = runtimeState.status
        if (status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING) {
            viewModel.toggleConnection()
            return
        }
        if (selectedNode == null) {
            viewModel.startConnectionFlow()
            return
        }
        val needsNotificationPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPromptedNotificationPerm &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPrompt) {
            showNotificationRationaleDialog = true
        } else {
            viewModel.startConnectionFlow()
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
                pendingVpnConsentIntent = intent
            } else {
                viewModel.onVpnPermissionGranted()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.requestInstallPermissionEvent.collect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                showInstallPermissionRationaleDialog = true
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

    fun openExternalUrl(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            scope.launch { snackbarHostState.showSnackbar("无法打开浏览器，请手动访问 Release 页面") }
        }
    }

    IProxyTheme {
        LiquidBackdrop(status = runtimeState.status) {
            Box(modifier = Modifier.fillMaxSize()) {
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
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onBackground,
                            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                            bottomBar = {
                                LiquidBottomNavBar(
                                    currentTab = currentTab,
                                    onSelectTab = { currentTab = it }
                                )
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
                                        onToggleConnect = { handleUserConnectToggle() },
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
                                        onImportRawText = { viewModel.importFromText(it) },
                                        onShowMessage = { msg ->
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        },
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
                                        isCheckingUpdate = isCheckingUpdate,
                                        onUpdateSettings = { viewModel.updateSettings(it) },
                                        onCheckUpdate = { viewModel.checkForAppUpdate(silentIfLatest = false) },
                                        onExportBackup = { viewModel.exportBackupJson() },
                                        onImportBackup = { viewModel.importBackupJson(it) },
                                        onCopyText = { text, tip -> copyToClipboard(text, tip) }
                                    )
                                }
                            }
                        }
                    }
                }

                SplashBrandOverlay(
                    visible = showSplashOverlay,
                    isCheckingUpdate = isCheckingUpdate
                )

                availableUpdate?.let { update ->
                    AppUpdateDialog(
                        updateInfo = update,
                        isDownloading = isDownloadingUpdate,
                        downloadProgress = updateDownloadProgress,
                        downloadError = updateDownloadError,
                        hasPendingInstallApk = pendingInstallApk != null,
                        signatureMismatch = signatureMismatch,
                        onDownloadAndInstall = { viewModel.downloadAndInstallUpdate() },
                        onRetryInstall = { viewModel.retryInstallPendingApk() },
                        onExportBackup = {
                            scope.launch {
                                val json = viewModel.exportBackupJson()
                                copyToClipboard(json, "已复制全部节点与配置备份 JSON 到剪贴板")
                            }
                        },
                        onSaveApkAndUninstall = { viewModel.savePendingApkAndUninstall() },
                        onOpenReleasePage = { openExternalUrl(update.releasePageUrl) },
                        onDismiss = { viewModel.dismissUpdateDialog() }
                    )
                }

                if (showNotificationRationaleDialog) {
                    IProxyAlertDialog(
                        onDismissRequest = {
                            showNotificationRationaleDialog = false
                            hasPromptedNotificationPerm = true
                            viewModel.startConnectionFlow()
                        },
                        title = { Text("通知权限用途说明") },
                        text = {
                            Text("开启通知权限后，系统状态栏可实时展示加密连接状态、当前节点与上下行速率（Android 前台服务规范建议）。暂不开启仍可直接连接并使用所有代理功能。")
                        },
                        confirmButton = {
                            Button(onClick = {
                                showNotificationRationaleDialog = false
                                hasPromptedNotificationPerm = true
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.startConnectionFlow()
                                }
                            }) {
                                Text("同意并申请")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showNotificationRationaleDialog = false
                                hasPromptedNotificationPerm = true
                                viewModel.startConnectionFlow()
                            }) {
                                Text("暂不开启，直接连接")
                            }
                        }
                    )
                }

                pendingVpnConsentIntent?.let { vpnIntent ->
                    IProxyAlertDialog(
                        onDismissRequest = { pendingVpnConsentIntent = null },
                        title = { Text("系统 VPN 授权说明") },
                        text = {
                            Text("点击连接将调用 Android 官方 VPN 授权弹窗以建立本地加密虚拟网卡，仅转发您的代理流量，不读取无关个人数据。拒绝授权后，节点管理、订阅与测速功能仍可正常使用。")
                        },
                        confirmButton = {
                            Button(onClick = {
                                val intentToLaunch = vpnIntent
                                pendingVpnConsentIntent = null
                                vpnPermLauncher.launch(intentToLaunch)
                            }) {
                                Text("前往系统授权")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingVpnConsentIntent = null }) {
                                Text("取消")
                            }
                        }
                    )
                }

                if (showInstallPermissionRationaleDialog) {
                    IProxyAlertDialog(
                        onDismissRequest = { showInstallPermissionRationaleDialog = false },
                        title = { Text("安装应用权限说明") },
                        text = {
                            Text("需要开启「允许安装来自此来源的应用」，以便调用系统安装器完成已校验更新包的升级。拒绝后现有代理功能不受影响，也可改用「浏览器下载」手动安装。")
                        },
                        confirmButton = {
                            Button(onClick = {
                                showInstallPermissionRationaleDialog = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    installPermLauncher.launch(intent)
                                }
                            }) {
                                Text("前往系统设置")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showInstallPermissionRationaleDialog = false }) {
                                Text("暂不授权")
                            }
                        }
                    )
                }
            }
        }
    }
}
