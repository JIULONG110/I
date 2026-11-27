package com.iproxy.app.ui

import android.app.Application
import android.content.Context
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iproxy.app.core.util.CoreLogBuffer
import com.iproxy.app.data.model.AppSettings
import com.iproxy.app.data.model.LogEntry
import com.iproxy.app.data.model.RoutingMode
import com.iproxy.app.data.model.ServerNode
import com.iproxy.app.data.model.Subscription
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

/**
 * 全局共享 ViewModel，统一向各个页面暴露状态与操作方法
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

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

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private val _requestVpnPermissionEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    val requestVpnPermissionEvent: SharedFlow<Unit> = _requestVpnPermissionEvent.asSharedFlow()

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
