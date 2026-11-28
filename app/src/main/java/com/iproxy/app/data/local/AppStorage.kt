package com.iproxy.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.iproxy.app.data.model.AppSettings
import com.iproxy.app.data.model.ServerNode
import com.iproxy.app.data.model.Subscription
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "i_proxy_settings")

/**
 * 本地持久化存储仓库：
 * - 使用 MMKV（多进程安全）存储节点列表、订阅列表与当前选中节点 ID（Service 与 UI 进程共享读写）
 * - 使用 DataStore 存储应用全局设置
 */
class AppStorage(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val mmkv: MMKV by lazy {
        MMKV.initialize(context.applicationContext)
        MMKV.mmkvWithID("i_proxy_store", MMKV.MULTI_PROCESS_MODE)
    }

    private val _nodesFlow = MutableStateFlow<List<ServerNode>>(emptyList())
    val nodesFlow: StateFlow<List<ServerNode>> = _nodesFlow.asStateFlow()

    private val _subscriptionsFlow = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptionsFlow: StateFlow<List<Subscription>> = _subscriptionsFlow.asStateFlow()

    private val _selectedNodeIdFlow = MutableStateFlow<String?>(null)
    val selectedNodeIdFlow: StateFlow<String?> = _selectedNodeIdFlow.asStateFlow()

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val raw = prefs[KEY_SETTINGS_JSON]
        if (raw.isNullOrBlank()) {
            AppSettings()
        } else {
            runCatching { gson.fromJson(raw, AppSettings::class.java)?.sanitized() }.getOrNull() ?: AppSettings()
        }
    }

    init {
        refreshFromMmkv()
    }

    fun refreshFromMmkv() {
        _nodesFlow.value = readNodesSync()
        _subscriptionsFlow.value = readSubscriptionsSync()
        _selectedNodeIdFlow.value = mmkv.decodeString(KEY_SELECTED_NODE_ID)
    }

    fun readNodesSync(): List<ServerNode> {
        val raw = mmkv.decodeString(KEY_NODES_JSON) ?: return emptyList()
        val type = object : TypeToken<List<ServerNode>>() {}.type
        return runCatching { gson.fromJson<List<ServerNode>>(raw, type) }
            .getOrNull()
            .orEmpty()
            .map { it.sanitized() }
    }

    fun saveNodesSync(nodes: List<ServerNode>) {
        mmkv.encode(KEY_NODES_JSON, gson.toJson(nodes))
        _nodesFlow.value = nodes
    }

    fun readSubscriptionsSync(): List<Subscription> {
        val raw = mmkv.decodeString(KEY_SUBSCRIPTIONS_JSON) ?: return emptyList()
        val type = object : TypeToken<List<Subscription>>() {}.type
        return runCatching { gson.fromJson<List<Subscription>>(raw, type) }.getOrNull() ?: emptyList()
    }

    fun saveSubscriptionsSync(subs: List<Subscription>) {
        mmkv.encode(KEY_SUBSCRIPTIONS_JSON, gson.toJson(subs))
        _subscriptionsFlow.value = subs
    }

    fun getSelectedNodeIdSync(): String? {
        return mmkv.decodeString(KEY_SELECTED_NODE_ID)
    }

    fun setSelectedNodeIdSync(nodeId: String?) {
        if (nodeId == null) {
            mmkv.removeValueForKey(KEY_SELECTED_NODE_ID)
        } else {
            mmkv.encode(KEY_SELECTED_NODE_ID, nodeId)
        }
        _selectedNodeIdFlow.value = nodeId
    }

    fun getSelectedNodeSync(): ServerNode? {
        val nodes = readNodesSync()
        val id = getSelectedNodeIdSync()
        return nodes.firstOrNull { it.id == id } ?: nodes.firstOrNull()
    }

    fun saveSettingsSnapshotSync(settings: AppSettings) {
        mmkv.encode(KEY_SETTINGS_SNAPSHOT, gson.toJson(settings))
    }

    fun readSettingsSnapshotSync(): AppSettings {
        val raw = mmkv.decodeString(KEY_SETTINGS_SNAPSHOT) ?: return AppSettings()
        return runCatching { gson.fromJson(raw, AppSettings::class.java)?.sanitized() }.getOrNull() ?: AppSettings()
    }

    suspend fun getSettingsOnce(): AppSettings {
        return settingsFlow.first()
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[KEY_SETTINGS_JSON]?.let {
                runCatching { gson.fromJson(it, AppSettings::class.java)?.sanitized() }.getOrNull()
            } ?: AppSettings()
            val updated = transform(current).sanitized()
            val json = gson.toJson(updated)
            prefs[KEY_SETTINGS_JSON] = json
            mmkv.encode(KEY_SETTINGS_SNAPSHOT, json)
        }
    }

    /**
     * 导出完整备份 JSON（含所有节点、订阅与应用设置）
     */
    suspend fun exportBackupJson(): String {
        val payload = BackupPayload(
            nodes = readNodesSync(),
            subscriptions = readSubscriptionsSync(),
            settings = getSettingsOnce()
        )
        return gson.toJson(payload)
    }

    /**
     * 从备份 JSON 恢复配置
     */
    @Suppress("USELESS_ELVIS")
    suspend fun importBackupJson(json: String): Boolean {
        val payload = runCatching { gson.fromJson(json, BackupPayload::class.java) }.getOrNull()
            ?: return false
        val rawNodes = (payload.nodes as List<ServerNode>?) ?: emptyList()
        val rawSubs = (payload.subscriptions as List<Subscription>?) ?: emptyList()
        val rawSettings = (payload.settings as AppSettings?)?.sanitized() ?: AppSettings()
        val sanitizedNodes = rawNodes.map { it.sanitized() }
        saveNodesSync(sanitizedNodes)
        saveSubscriptionsSync(rawSubs)
        updateSettings { rawSettings }
        if (getSelectedNodeIdSync() == null && sanitizedNodes.isNotEmpty()) {
            setSelectedNodeIdSync(sanitizedNodes.first().id)
        }
        return true
    }

    data class BackupPayload(
        val nodes: List<ServerNode> = emptyList(),
        val subscriptions: List<Subscription> = emptyList(),
        val settings: AppSettings = AppSettings()
    )

    companion object {
        private const val KEY_NODES_JSON = "nodes_json"
        private const val KEY_SUBSCRIPTIONS_JSON = "subscriptions_json"
        private const val KEY_SELECTED_NODE_ID = "selected_node_id"
        private const val KEY_SETTINGS_SNAPSHOT = "settings_snapshot_json"
        private val KEY_SETTINGS_JSON = stringPreferencesKey("settings_json")
    }
}
