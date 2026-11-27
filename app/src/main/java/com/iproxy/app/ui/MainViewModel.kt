package com.iproxy.app.ui

import android.app.Application
import android.content.Context
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iproxy.app.core.update.AppUpdateInfo
import com.iproxy.app.core.update.AppUpdateManager
import com.iproxy.app.core.update.InstallLaunchResult
import com.iproxy.app.core.util.CoreLogBuffer
import com.iproxy.app.data.model.AppSettings
import com.iproxy.app.data.model.LogEntry
import com.iproxy.app.data.model.RoutingMode
import com.iproxy.app.data.model.ServerNode
import com.iproxy.app.data.model.Subscription
import com.iproxy.app.data.model.ThemeMode
import com.iproxy.app.data.model.VpnRuntimeState
import com.iproxy.app.data.model.VpnStatus
import com.iproxy.app.data.repository.AppRepository
import com.iproxy.app.data.repository.InstalledAppInfo
import com.iproxy.app.service.IQuickTileService
import com.iproxy.app.service.IVpnService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * 全局共享 ViewModel，统一向各个页面暴露状态与操作方法
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val updateManager = AppUpdateManager(application)

    val nodes: StateFlow<List<ServerNode>> = repository.nodesFlow
    val subscriptions: StateFlow<List<Subscription>> = repository.subscriptionsFlow
    val selectedNodeId: StateFlow<String?> = repository.selectedNodeIdFlow
    val runtimeState: StateFlow<VpnRuntimeState> = IVpnService.runtimeState
    val logs: StateFlow<List<LogEntry>> = CoreLogBuffer.logsFlow

    val settings: StateFlow<AppSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = repository.storage.readSettingsSnapshotSync()
    )

    val selectedNode: StateFlow<ServerNode?> = combine(nodes, selectedNodeId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isTestingLatency = MutableStateFlow(false)
    val isTestingLatency: StateFlow<Boolean> = _isTestingLatency.asStateFlow()

    private val _isRefreshingSubs = MutableStateFlow(false)
    val isRefreshingSubs: StateFlow<Boolean> = _isRefreshingSubs.asStateFlow()

    private val _isUpdatingGeo = MutableStateFlow(false)
    val isUpdatingGeo: StateFlow<Boolean> = _isUpdatingGeo.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val availableUpdate: StateFlow<AppUpdateInfo?> = _availableUpdate.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _isDownloadingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate: StateFlow<Boolean> = _isDownloadingUpdate.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow<Float?>(null)
    val updateDownloadProgress: StateFlow<Float?> = _updateDownloadProgress.asStateFlow()

    private val _updateDownloadError = MutableStateFlow<String?>(null)
    val updateDownloadError: StateFlow<String?> = _updateDownloadError.asStateFlow()

    private val _pendingInstallApk = MutableStateFlow<File?>(null)
    val pendingInstallApk: StateFlow<File?> = _pendingInstallApk.asStateFlow()

    private val _signatureMismatch = MutableStateFlow<InstallLaunchResult.SignatureMismatch?>(null)
    val signatureMismatch: StateFlow<InstallLaunchResult.SignatureMismatch?> = _signatureMismatch.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private val _requestVpnPermissionEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    val requestVpnPermissionEvent: SharedFlow<Unit> = _requestVpnPermissionEvent.asSharedFlow()

    private val _requestInstallPermissionEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    val requestInstallPermissionEvent: SharedFlow<Unit> = _requestInstallPermissionEvent.asSharedFlow()

    private var initialCheckFailed = false
    private var hasCompletedUpdateCheck = false

    init {
        checkForAppUpdate(silentIfLatest = true)
        viewModelScope.launch {
            runtimeState.collect { state ->
                if (state.status == VpnStatus.CONNECTED && initialCheckFailed && !hasCompletedUpdateCheck) {
                    checkForAppUpdate(silentIfLatest = true)
                }
            }
        }
    }

    fun checkForAppUpdate(silentIfLatest: Boolean = false) {
        if (_isCheckingUpdate.value) return
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            updateManager.checkForUpdate()
                .onSuccess { info ->
                    hasCompletedUpdateCheck = true
                    initialCheckFailed = false
                    if (info != null) {
                        _updateDownloadError.value = null
                        _updateDownloadProgress.value = null
                        _pendingInstallApk.value = null
                        _signatureMismatch.value = null
                        _availableUpdate.value = info
                    } else if (!silentIfLatest) {
                        emitToast("当前已是最新版本")
                    }
                }
                .onFailure { err ->
                    initialCheckFailed = true
                    if (!silentIfLatest) {
                        emitToast("检查更新失败: ${err.message ?: "请检查网络"}")
                    }
                }
            _isCheckingUpdate.value = false
        }
    }

    fun dismissUpdateDialog() {
        if (_isDownloadingUpdate.value) return
        _availableUpdate.value = null
        _updateDownloadProgress.value = null
        _updateDownloadError.value = null
        _pendingInstallApk.value = null
        _signatureMismatch.value = null
    }

    fun downloadAndInstallUpdate() {
        val update = _availableUpdate.value ?: return
        val asset = update.apkAsset ?: return
        if (_isDownloadingUpdate.value) return

        viewModelScope.launch {
            _isDownloadingUpdate.value = true
            _updateDownloadError.value = null
            _updateDownloadProgress.value = null
            _signatureMismatch.value = null

            updateManager.downloadApk(asset) { downloaded, total ->
                _updateDownloadProgress.value = if (total > 0L) {
                    (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                } else {
                    null
                }
            }.onSuccess { apkFile ->
                _pendingInstallApk.value = apkFile
                handleInstallLaunchResult(updateManager.launchApkInstaller(apkFile))
            }.onFailure { err ->
                _updateDownloadError.value = "下载失败: ${err.message ?: "网络异常"}"
            }

            _isDownloadingUpdate.value = false
        }
    }

    fun retryInstallPendingApk() {
        val apkFile = _pendingInstallApk.value ?: return
        handleInstallLaunchResult(updateManager.launchApkInstaller(apkFile))
    }

    fun savePendingApkAndUninstall() {
        val apkFile = _pendingInstallApk.value ?: return
        viewModelScope.launch {
            updateManager.saveApkToPublicDownloads(apkFile)
                .onSuccess { savedRelPath ->
                    emitToast("安装包已保存到 $savedRelPath，卸载后请在文件管理中打开安装")
                    updateManager.launchUninstallCurrentApp()
                        .onFailure { err ->
                            _updateDownloadError.value = "无法拉起卸载界面: ${err.message}"
                        }
                }
                .onFailure { err ->
                    _updateDownloadError.value = err.message ?: "保存安装包失败，请点击「浏览器下载」"
                }
        }
    }

    private fun handleInstallLaunchResult(result: InstallLaunchResult) {
        when (result) {
            InstallLaunchResult.Launched -> {
                _updateDownloadError.value = null
                _signatureMismatch.value = null
            }
            InstallLaunchResult.NeedsInstallPermission -> {
                emitToast("请先开启「允许来自此来源的应用」安装权限")
                _requestInstallPermissionEvent.tryEmit(Unit)
            }
            is InstallLaunchResult.SignatureMismatch -> {
                _updateDownloadError.value = null
                _signatureMismatch.value = result
            }
            is InstallLaunchResult.Failed -> {
                _updateDownloadError.value = "启动安装失败: ${result.reason}"
            }
        }
    }

    fun cycleThemeMode() {
        updateSettings { current ->
            val next = when (current.themeMode) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            }
            current.copy(themeMode = next)
        }
    }

    fun selectNode(nodeId: String) {
        repository.selectNode(nodeId)
        IQuickTileService.requestTileSync(getApplication())
        // 若当前已处于连接状态，切换节点后自动热重连以立即生效
        val status = runtimeState.value.status
        if (status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING) {
            startConnectionFlow()
        }
    }

    fun toggleConnection() {
        val current = runtimeState.value.status
        if (current == VpnStatus.CONNECTED || current == VpnStatus.CONNECTING) {
            IVpnService.stop(getApplication())
        } else {
            startConnectionFlow()
        }
    }

    fun startConnectionFlow() {
        val node = selectedNode.value
        if (node == null) {
            emitToast("请先导入或添加一个节点")
            return
        }
        val app = getApplication<Application>()
        val curSettings = settings.value
        if (!curSettings.proxyOnlyMode && VpnService.prepare(app) != null) {
            _requestVpnPermissionEvent.tryEmit(Unit)
            return
        }
        IVpnService.start(app)
    }

    fun onVpnPermissionGranted() {
        IVpnService.start(getApplication())
    }

    fun setRoutingMode(mode: RoutingMode) {
        updateSettings { it.copy(routingMode = mode) }
        if (runtimeState.value.status == VpnStatus.CONNECTED) {
            emitToast("已切换分流模式为「${mode.title}」，正在应用...")
            startConnectionFlow()
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            repository.storage.updateSettings(transform)
        }
    }

    fun importFromText(raw: String) {
        viewModelScope.launch {
            _isRefreshingSubs.value = true
            repository.importFromText(raw)
                .onSuccess { count ->
                    emitToast("成功导入 $count 个节点")
                }
                .onFailure { err ->
                    emitToast(err.message ?: "导入失败")
                }
            _isRefreshingSubs.value = false
        }
    }

    fun saveNode(node: ServerNode) {
        repository.upsertNode(node)
        emitToast("节点「${node.name}」已保存")
    }

    fun deleteNode(nodeId: String) {
        repository.deleteNode(nodeId)
        emitToast("已删除节点")
    }

    fun addSubscription(name: String, url: String, autoUpdate: Boolean) {
        viewModelScope.launch {
            _isRefreshingSubs.value = true
            val sub = Subscription(
                name = name.ifBlank { "我的订阅" },
                url = url.trim(),
                autoUpdateEnabled = autoUpdate
            )
            repository.addAndRefreshSubscription(sub)
                .onSuccess { count -> emitToast("订阅添加成功，共拉取 $count 个节点") }
                .onFailure { err -> emitToast("订阅拉取失败: ${err.message}") }
            _isRefreshingSubs.value = false
        }
    }

    fun refreshSubscription(subId: String) {
        viewModelScope.launch {
            _isRefreshingSubs.value = true
            repository.refreshSubscription(subId)
                .onSuccess { count -> emitToast("订阅已更新，$count 个节点") }
                .onFailure { err -> emitToast("更新失败: ${err.message}") }
            _isRefreshingSubs.value = false
        }
    }

    fun refreshAllSubscriptions() {
        viewModelScope.launch {
            if (subscriptions.value.isEmpty()) {
                emitToast("暂无订阅可更新")
                return@launch
            }
            _isRefreshingSubs.value = true
            val count = repository.refreshAllSubscriptions()
            emitToast("全部订阅刷新完成，共更新 $count 个节点")
            _isRefreshingSubs.value = false
        }
    }

    fun deleteSubscription(subId: String) {
        repository.deleteSubscription(subId, removeNodes = true)
        emitToast("已移除订阅及其下属节点")
    }

    fun measureAllNodes() {
        viewModelScope.launch {
            if (nodes.value.isEmpty()) {
                emitToast("暂无节点可测速")
                return@launch
            }
            _isTestingLatency.value = true
            repository.measureNodesDelay()
            _isTestingLatency.value = false
            emitToast("全部节点真连接测速完成")
        }
    }

    fun measureSingleNode(nodeId: String) {
        viewModelScope.launch {
            _isTestingLatency.value = true
            repository.measureNodesDelay(listOf(nodeId))
            _isTestingLatency.value = false
        }
    }

    fun sortByLatency() {
        repository.sortNodesByLatency()
        emitToast("已按延迟从低到高排序")
    }

    fun clearUnreachableNodes() {
        repository.clearUnreachableNodes()
        emitToast("已清理超时不可用节点")
    }

    fun updateGeoDatabases() {
        viewModelScope.launch {
            _isUpdatingGeo.value = true
            repository.updateGeoDatabases()
                .onSuccess { emitToast("GeoIP / GeoSite 规则库已更新至最新版") }
                .onFailure { err -> emitToast("规则库更新失败: ${err.message}") }
            _isUpdatingGeo.value = false
        }
    }

    fun loadInstalledAppsIfNeeded() {
        if (_installedApps.value.isNotEmpty() || _isLoadingApps.value) return
        viewModelScope.launch {
            _isLoadingApps.value = true
            _installedApps.value = repository.loadInstalledApps()
            _isLoadingApps.value = false
        }
    }

    fun toggleAppSelection(packageName: String) {
        updateSettings { cur ->
            val next = cur.selectedAppPackages.toMutableSet()
            if (!next.add(packageName)) {
                next.remove(packageName)
            }
            cur.copy(selectedAppPackages = next)
        }
    }

    fun exportNodeShareLink(node: ServerNode): String = repository.exportNodeShareLink(node)

    suspend fun exportBackupJson(): String = repository.storage.exportBackupJson()

    fun importBackupJson(json: String) {
        viewModelScope.launch {
            val ok = repository.storage.importBackupJson(json)
            emitToast(if (ok) "配置备份已恢复" else "备份格式无效，请检查内容")
        }
    }

    fun clearLogs() {
        CoreLogBuffer.clear()
        emitToast("日志已清空")
    }

    fun exportLogsText(): String = CoreLogBuffer.exportAsText()

    fun getContext(): Context = getApplication()

    private fun emitToast(message: String) {
        _toastEvents.tryEmit(message)
    }
}
