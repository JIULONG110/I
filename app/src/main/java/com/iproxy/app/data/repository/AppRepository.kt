package com.iproxy.app.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.iproxy.app.core.parser.ShareLinkParser
import com.iproxy.app.core.parser.SubscriptionParser
import com.iproxy.app.core.util.CoreLogBuffer
import com.iproxy.app.core.xray.XrayConfigGenerator
import com.iproxy.app.data.local.AppStorage
import com.iproxy.app.data.model.AppSettings
import com.iproxy.app.data.model.ServerNode
import com.iproxy.app.data.model.Subscription
import com.iproxy.app.service.IVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import libv2ray.Libv2ray
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 已安装应用信息（用于分应用代理选择器）
 */
data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean
)

/**
 * 核心业务仓库：统一管理节点、订阅拉取、真连接测速、规则库更新与系统应用列表
 */
class AppRepository(
    private val context: Context,
    val storage: AppStorage = AppStorage(context)
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val nodesFlow: StateFlow<List<ServerNode>> = storage.nodesFlow
    val subscriptionsFlow: StateFlow<List<Subscription>> = storage.subscriptionsFlow
    val selectedNodeIdFlow: StateFlow<String?> = storage.selectedNodeIdFlow
    val settingsFlow: Flow<AppSettings> = storage.settingsFlow

    fun selectNode(nodeId: String) {
        storage.setSelectedNodeIdSync(nodeId)
    }

    fun upsertNode(node: ServerNode) {
        val current = storage.readNodesSync().toMutableList()
        val idx = current.indexOfFirst { it.id == node.id }
        if (idx >= 0) {
            current[idx] = node
        } else {
            current.add(0, node)
        }
        storage.saveNodesSync(current)
        if (storage.getSelectedNodeIdSync() == null) {
            storage.setSelectedNodeIdSync(node.id)
        }
    }

    fun deleteNode(nodeId: String) {
        val remaining = storage.readNodesSync().filterNot { it.id == nodeId }
        storage.saveNodesSync(remaining)
        if (storage.getSelectedNodeIdSync() == nodeId) {
            storage.setSelectedNodeIdSync(remaining.firstOrNull()?.id)
        }
    }

    fun clearUnreachableNodes() {
        val remaining = storage.readNodesSync().filter { it.latencyMs == null || it.latencyMs > 0 }
        storage.saveNodesSync(remaining)
        if (remaining.none { it.id == storage.getSelectedNodeIdSync() }) {
            storage.setSelectedNodeIdSync(remaining.firstOrNull()?.id)
        }
    }

    fun sortNodesByLatency() {
        val sorted = storage.readNodesSync().sortedWith(
            compareBy<ServerNode> {
                when {
                    it.latencyMs == null -> Long.MAX_VALUE - 1
                    it.latencyMs < 0 -> Long.MAX_VALUE
                    else -> it.latencyMs
                }
            }.thenBy { it.name }
        )
        storage.saveNodesSync(sorted)
    }

    /**
     * 从任意文本（剪贴板/二维码内容）导入单条或多条节点；
     * 若检测到 http(s):// 订阅链接（非代理节点），自动作为订阅导入并拉取
     */
    suspend fun importFromText(rawText: String): Result<Int> = withContext(Dispatchers.IO) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("导入内容为空"))
        }

        // 检查是否单条 http(s) 订阅链接（没有端口凭据且不含代理关键字，或带常见订阅路径）
        val isSingleHttpSub = !trimmed.contains('\n') &&
            (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)) &&
            !trimmed.contains('@')

        if (isSingleHttpSub) {
            val sub = Subscription(
                name = "订阅 ${storage.readSubscriptionsSync().size + 1}",
                url = trimmed
            )
            return@withContext addAndRefreshSubscription(sub)
        }

        val parsed = SubscriptionParser.parseContent(trimmed)
        if (parsed.nodes.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("未能识别出有效的代理节点链接"))
        }

        val current = storage.readNodesSync().toMutableList()
        current.addAll(0, parsed.nodes)
        storage.saveNodesSync(current)
        if (storage.getSelectedNodeIdSync() == null) {
            storage.setSelectedNodeIdSync(parsed.nodes.first().id)
        }
        CoreLogBuffer.i("Import", "成功导入 ${parsed.nodes.size} 个节点")
        Result.success(parsed.nodes.size)
    }

    suspend fun addAndRefreshSubscription(sub: Subscription): Result<Int> {
        val list = storage.readSubscriptionsSync().toMutableList()
        val existingIdx = list.indexOfFirst { it.id == sub.id || it.url == sub.url }
        val targetSub = if (existingIdx >= 0) {
            sub.copy(id = list[existingIdx].id).also { list[existingIdx] = it }
        } else {
            sub.also { list.add(it) }
        }
        storage.saveSubscriptionsSync(list)
        return refreshSubscription(targetSub.id)
    }

    fun deleteSubscription(subId: String, removeNodes: Boolean = true) {
        val remainingSubs = storage.readSubscriptionsSync().filterNot { it.id == subId }
        storage.saveSubscriptionsSync(remainingSubs)
        if (removeNodes) {
            val remainingNodes = storage.readNodesSync().filterNot { it.subscriptionId == subId }
            storage.saveNodesSync(remainingNodes)
            if (remainingNodes.none { it.id == storage.getSelectedNodeIdSync() }) {
                storage.setSelectedNodeIdSync(remainingNodes.firstOrNull()?.id)
            }
        }
    }

    suspend fun refreshSubscription(subId: String): Result<Int> = withContext(Dispatchers.IO) {
        val subs = storage.readSubscriptionsSync().toMutableList()
        val index = subs.indexOfFirst { it.id == subId }
        if (index < 0) {
            return@withContext Result.failure(IllegalArgumentException("订阅不存在"))
        }
        val sub = subs[index]
        runCatching {
            val req = Request.Builder()
                .url(sub.url)
                .header("User-Agent", "I-Android/1.0 v2rayNG")
                .build()
            val (bodyText, userInfoHeader) = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("服务器返回错误状态码: ${resp.code}")
                }
                val body = resp.body.string()
                val header = resp.header("Subscription-Userinfo")
                    ?: resp.header("subscription-userinfo")
                body to header
            }

            val parsed = SubscriptionParser.parseContent(
                rawBody = bodyText,
                subscriptionId = sub.id,
                userInfoHeader = userInfoHeader
            )
            if (parsed.nodes.isEmpty()) {
                error("订阅响应中未包含任何可解析的节点")
            }

            // 用最新节点替换该订阅下的旧节点，保留其余分组节点
            val oldSelectedId = storage.getSelectedNodeIdSync()
            val otherNodes = storage.readNodesSync().filterNot { it.subscriptionId == sub.id }
            val merged = otherNodes + parsed.nodes
            storage.saveNodesSync(merged)

            if (merged.none { it.id == oldSelectedId }) {
                storage.setSelectedNodeIdSync(parsed.nodes.first().id)
            }

            subs[index] = sub.copy(
                lastUpdatedAt = System.currentTimeMillis(),
                nodeCount = parsed.nodes.size,
                uploadBytes = if (parsed.uploadBytes >= 0) parsed.uploadBytes else sub.uploadBytes,
                downloadBytes = if (parsed.downloadBytes >= 0) parsed.downloadBytes else sub.downloadBytes,
                totalBytes = if (parsed.totalBytes > 0) parsed.totalBytes else sub.totalBytes,
                expireTimestampSec = if (parsed.expireTimestampSec > 0) parsed.expireTimestampSec else sub.expireTimestampSec,
                lastError = null
            )
            storage.saveSubscriptionsSync(subs)
            CoreLogBuffer.i("Subscription", "订阅 [${sub.name}] 更新成功，共 ${parsed.nodes.size} 个节点")
            parsed.nodes.size
        }.onFailure { err ->
            subs[index] = sub.copy(lastError = err.message ?: "更新失败")
            storage.saveSubscriptionsSync(subs)
            CoreLogBuffer.e("Subscription", "订阅 [${sub.name}] 更新失败", err)
        }
    }

    suspend fun refreshAllSubscriptions(): Int {
        val subs = storage.readSubscriptionsSync()
        var totalUpdated = 0
        for (sub in subs) {
            refreshSubscription(sub.id).onSuccess { totalUpdated += it }
        }
        return totalUpdated
    }

    /**
     * 使用 Xray 内核并发测速（真连接 HTTP 204 延迟）
     */
    suspend fun measureNodesDelay(nodeIds: List<String>? = null): Unit = withContext(Dispatchers.IO) {
        val settings = storage.getSettingsOnce()
        val assetDir = IVpnService.ensureXrayAssetsDirectory(context)
        Libv2ray.initCoreEnv(assetDir.absolutePath, "")

        val allNodes = storage.readNodesSync()
        val targets = if (nodeIds == null) allNodes else allNodes.filter { it.id in nodeIds }
        val semaphore = Semaphore(6)

        val results = coroutineScope {
            targets.map { node ->
                async {
                    semaphore.withPermit {
                        val delayMs = runCatching {
                            val cfg = XrayConfigGenerator.generateSpeedTestConfig(node, settings, allNodes)
                            Libv2ray.measureOutboundDelay(cfg, settings.speedTestUrl)
                        }.getOrElse { -1L }
                        node.id to delayMs
                    }
                }
            }.awaitAll().toMap()
        }

        val now = System.currentTimeMillis()
        val updated = storage.readNodesSync().map { node ->
            val measured = results[node.id]
            if (measured != null) {
                node.copy(latencyMs = measured, lastTestedAt = now)
            } else {
                node
            }
        }
        storage.saveNodesSync(updated)
    }

    /**
     * 在线更新最新的 geoip.dat 与 geosite.dat 分流规则库
     */
    suspend fun updateGeoDatabases(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = IVpnService.ensureXrayAssetsDirectory(context)
            val urls = mapOf(
                "geoip.dat" to "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat",
                "geosite.dat" to "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"
            )
            urls.forEach { (name, url) ->
                val req = Request.Builder().url(url).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("下载 $name 失败: HTTP ${resp.code}")
                    val tmpFile = File(dir, "$name.tmp")
                    resp.body.byteStream().use { input ->
                        tmpFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (tmpFile.length() < 500_000L) error("$name 下载内容异常")
                    val target = File(dir, name)
                    if (target.exists()) target.delete()
                    tmpFile.renameTo(target)
                }
            }
            CoreLogBuffer.i("GeoData", "GeoIP 与 GeoSite 规则库已更新至最新版")
        }
    }

    /**
     * 读取手机已安装应用列表，供分应用代理配置使用
     */
    suspend fun loadInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
        apps.filter { it.packageName != context.packageName }
            .map { info ->
                val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val label = runCatching { pm.getApplicationLabel(info).toString() }
                    .getOrDefault(info.packageName)
                InstalledAppInfo(
                    packageName = info.packageName,
                    label = label,
                    isSystemApp = isSystem
                )
            }
            .sortedWith(compareBy<InstalledAppInfo> { it.isSystemApp }.thenBy { it.label.lowercase() })
    }

    fun exportNodeShareLink(node: ServerNode): String = ShareLinkParser.toShareLink(node)
}
