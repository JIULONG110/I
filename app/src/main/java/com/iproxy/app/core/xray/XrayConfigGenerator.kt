package com.iproxy.app.core.xray

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.iproxy.app.data.model.AppSettings
import com.iproxy.app.data.model.PolicyStrategy
import com.iproxy.app.data.model.ProtocolType
import com.iproxy.app.data.model.RoutingMode
import com.iproxy.app.data.model.SecurityMode
import com.iproxy.app.data.model.ServerNode
import com.iproxy.app.data.model.TransportNetwork
import java.util.Base64

/**
 * Xray-core JSON 运行时配置生成器
 *
 * 严格兼容 AndroidLibXrayLite v26.11.26 (Xray-core v26.x) 规范：
 * 支持 VLESS / VMess / Trojan / Shadowsocks / Hysteria2 / WireGuard / MASQUE / SOCKS / HTTP /
 * 策略组 (PolicyGroup) / 链式代理 (ProxyChain) / 自定义出站 (CustomOutbound) / 自定义配置 (CustomJson)
 */
object XrayConfigGenerator {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private const val PROXY_BALANCER_TAG = "proxy-balancer"

    private data class BuiltProxyPlan(
        val outbounds: List<JsonObject>,
        val balancerTag: String? = null,
        val balancers: JsonArray = JsonArray(),
        val observatory: JsonObject? = null,
        val burstObservatory: JsonObject? = null
    )

    /**
     * 生成完整运行时 Xray JSON 配置字符串
     */
    fun generateConfig(
        node: ServerNode,
        settings: AppSettings,
        resolvedHosts: Map<String, List<String>> = emptyMap(),
        allNodes: List<ServerNode> = emptyList()
    ): String {
        if (node.protocol == ProtocolType.CUSTOM_JSON && node.customRawJson.isNotBlank()) {
            val customRoot = runCatching {
                JsonParser.parseString(node.customRawJson).asJsonObject
            }.getOrNull()
            if (customRoot != null) {
                return patchCustomConfig(customRoot, settings)
            }
        }

        val plan = buildProxyPlan(node, settings, resolvedHosts, allNodes)
        val root = JsonObject()

        // 1. 日志配置
        root.add("log", JsonObject().apply {
            addProperty("loglevel", "warning")
        })

        // 2. 流量统计与策略配置（驱动主页实时网速与流量面板）
        root.add("stats", JsonObject())
        root.add("policy", buildStatsPolicy())

        // 3. 入站监听（本地 SOCKS5 供 hev-socks5-tunnel 使用 + 本地 HTTP 代理）
        root.add("inbounds", buildInbounds(settings))

        // 4. 出站节点（proxy / proxy-* + chain-* + direct + block + dns-out）
        val outbounds = JsonArray()
        plan.outbounds.forEach { outbounds.add(it) }
        outbounds.add(buildDirectOutbound(settings))
        outbounds.add(buildBlockOutbound())
        outbounds.add(buildDnsOutbound())
        root.add("outbounds", outbounds)

        // 5. Observatory 探测（仅策略组需要）
        plan.observatory?.let { root.add("observatory", it) }
        plan.burstObservatory?.let { root.add("burstObservatory", it) }

        // 6. DNS 与可选 FakeDNS
        root.add("dns", buildDns(settings, resolvedHosts))
        if (settings.enableFakeDns) {
            root.add("fakedns", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("ipPool", "198.18.0.0/15")
                    addProperty("poolSize", 65535)
                })
            })
        }

        // 7. 路由分流与均衡器规则
        root.add("routing", buildRouting(settings, plan))

        return gson.toJson(root)
    }

    /**
     * 生成专用于单节点 / 策略组 / 链式代理真连接测速的最小 Xray 配置
     */
    fun generateSpeedTestConfig(
        node: ServerNode,
        settings: AppSettings,
        allNodes: List<ServerNode> = emptyList()
    ): String {
        if (node.protocol == ProtocolType.CUSTOM_JSON && node.customRawJson.isNotBlank()) {
            val customRoot = runCatching {
                JsonParser.parseString(node.customRawJson).asJsonObject
            }.getOrNull()
            if (customRoot != null && customRoot.has("outbounds")) {
                return customRoot.toString()
            }
        }
        val plan = buildProxyPlan(node, settings, emptyMap(), allNodes)
        val root = JsonObject()
        root.add("log", JsonObject().apply { addProperty("loglevel", "none") })
        root.add("outbounds", JsonArray().apply {
            plan.outbounds.forEach { add(it) }
            add(buildDirectOutbound(settings))
        })
        plan.observatory?.let { root.add("observatory", it) }
        plan.burstObservatory?.let { root.add("burstObservatory", it) }
        if (plan.balancerTag != null) {
            root.add("routing", JsonObject().apply {
                addProperty("domainStrategy", "AsIs")
                add("balancers", plan.balancers)
                add("rules", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("type", "field")
                        addProperty("network", "tcp,udp")
                        addProperty("balancerTag", plan.balancerTag)
                    })
                })
            })
        }
        return root.toString()
    }

    /**
     * 收集当前节点及其组合子节点所需预解析的全部非 IP 服务器域名
     */
    fun collectTargetHosts(node: ServerNode, allNodes: List<ServerNode> = emptyList()): List<String> {
        val nodeMap = allNodes.associateBy { it.id }
        val result = linkedSetOf<String>()
        collectTargetHostsRecursive(node, nodeMap, allNodes, linkedSetOf(), result)
        return result.toList()
    }

    private fun collectTargetHostsRecursive(
        node: ServerNode,
        nodeMap: Map<String, ServerNode>,
        allNodes: List<ServerNode>,
        visitedIds: MutableSet<String>,
        outHosts: MutableSet<String>
    ) {
        if (!visitedIds.add(node.id)) return
        when (node.protocol) {
            ProtocolType.POLICY_GROUP -> {
                resolvePolicyGroupMembers(node, nodeMap, allNodes, visitedIds).forEach { child ->
                    collectTargetHostsRecursive(child, nodeMap, allNodes, visitedIds, outHosts)
                }
            }
            ProtocolType.PROXY_CHAIN -> {
                resolveProxyChainHops(node, nodeMap, visitedIds).forEach { hop ->
                    collectTargetHostsRecursive(hop, nodeMap, allNodes, visitedIds, outHosts)
                }
            }
            ProtocolType.CUSTOM_JSON, ProtocolType.CUSTOM_OUTBOUND -> Unit
            else -> {
                val host = node.server.trim()
                if (host.isNotEmpty()) outHosts.add(host)
            }
        }
    }

    private fun buildStatsPolicy(): JsonObject = JsonObject().apply {
        add("levels", JsonObject().apply {
            add("8", JsonObject().apply {
                addProperty("handshake", 4)
                addProperty("connIdle", 300)
                addProperty("uplinkOnly", 1)
                addProperty("downlinkOnly", 1)
            })
        })
        add("system", JsonObject().apply {
            addProperty("statsOutboundUplink", true)
            addProperty("statsOutboundDownlink", true)
        })
    }

    private fun patchCustomConfig(customRoot: JsonObject, settings: AppSettings): String {
        val clonedRoot = customRoot.deepCopy()
        // 若是单个 outbound 对象而非完整配置，自动包裹进完整模板
        if (!clonedRoot.has("outbounds") && clonedRoot.has("protocol")) {
            val cloned = clonedRoot.apply { addProperty("tag", "proxy") }
            val root = JsonObject()
            root.add("log", JsonObject().apply { addProperty("loglevel", "warning") })
            root.add("stats", JsonObject())
            root.add("policy", buildStatsPolicy())
            root.add("inbounds", buildInbounds(settings))
            root.add("outbounds", JsonArray().apply {
                add(cloned)
                add(buildDirectOutbound(settings))
                add(buildBlockOutbound())
                add(buildDnsOutbound())
            })
            root.add("dns", buildDns(settings))
            root.add("routing", buildRouting(settings, BuiltProxyPlan(listOf(cloned))))
            return gson.toJson(root)
        }

        if (!clonedRoot.has("inbounds")) {
            clonedRoot.add("inbounds", buildInbounds(settings))
        }
        if (!clonedRoot.has("stats")) {
            clonedRoot.add("stats", JsonObject())
        }
        return gson.toJson(clonedRoot)
    }

    private fun buildProxyPlan(
        node: ServerNode,
        settings: AppSettings,
        resolvedHosts: Map<String, List<String>>,
        allNodes: List<ServerNode>
    ): BuiltProxyPlan {
        val nodeMap = allNodes.associateBy { it.id }

        if (node.protocol == ProtocolType.POLICY_GROUP) {
            val members = resolvePolicyGroupMembers(node, nodeMap, allNodes, linkedSetOf(node.id))
            check(members.isNotEmpty()) { "策略组「${node.name}」未包含任何可用子节点，请先添加节点" }

            if (members.size == 1) {
                val singleOutbounds = buildOutboundsForTarget(
                    target = members.first(),
                    settings = settings,
                    resolvedHosts = resolvedHosts,
                    nodeMap = nodeMap,
                    visitedIds = linkedSetOf(node.id),
                    baseTag = "proxy",
                    dialerProxyTag = null
                )
                return BuiltProxyPlan(outbounds = singleOutbounds)
            }

            val combinedOutbounds = mutableListOf<JsonObject>()
            val firstMemberTag = "proxy-1"
            members.forEachIndexed { index, child ->
                val childBaseTag = "proxy-${index + 1}"
                combinedOutbounds.addAll(
                    buildOutboundsForTarget(
                        target = child,
                        settings = settings,
                        resolvedHosts = resolvedHosts,
                        nodeMap = nodeMap,
                        visitedIds = linkedSetOf(node.id),
                        baseTag = childBaseTag,
                        dialerProxyTag = null
                    )
                )
            }

            val selectorPrefix = "proxy-"
            val probeUrl = settings.speedTestUrl.ifBlank { "https://www.gstatic.com/generate_204" }
            var observatory: JsonObject? = null
            var burstObservatory: JsonObject? = null

            when (node.policyStrategy) {
                PolicyStrategy.LEAST_PING -> {
                    observatory = JsonObject().apply {
                        add("subjectSelector", JsonArray().apply { add(selectorPrefix) })
                        addProperty("probeURL", probeUrl)
                        addProperty("probeInterval", "3m")
                        addProperty("enableConcurrency", true)
                    }
                }
                PolicyStrategy.FALLBACK, PolicyStrategy.LEAST_LOAD -> {
                    burstObservatory = JsonObject().apply {
                        add("subjectSelector", JsonArray().apply { add(selectorPrefix) })
                        add("pingConfig", JsonObject().apply {
                            addProperty("destination", probeUrl)
                            addProperty("interval", "5m")
                            addProperty("timeout", "30s")
                            addProperty("sampling", 2)
                        })
                    }
                }
                PolicyStrategy.ROUND_ROBIN, PolicyStrategy.RANDOM -> Unit
            }

            val strategyType = when (node.policyStrategy) {
                PolicyStrategy.FALLBACK -> PolicyStrategy.LEAST_LOAD.value
                else -> node.policyStrategy.value
            }
            val strategyObj = JsonObject().apply {
                addProperty("type", strategyType)
                if (strategyType == PolicyStrategy.LEAST_LOAD.value) {
                    add("settings", JsonObject().apply {
                        addProperty("expected", 1)
                        if (node.policyStrategy == PolicyStrategy.FALLBACK) {
                            addProperty("tolerance", 0.2)
                            addProperty("maxRTT", "5000ms")
                        }
                    })
                }
            }
            val balancerObj = JsonObject().apply {
                addProperty("tag", PROXY_BALANCER_TAG)
                add("selector", JsonArray().apply { add(selectorPrefix) })
                addProperty("fallbackTag", firstMemberTag)
                add("strategy", strategyObj)
            }

            return BuiltProxyPlan(
                outbounds = combinedOutbounds,
                balancerTag = PROXY_BALANCER_TAG,
                balancers = JsonArray().apply { add(balancerObj) },
                observatory = observatory,
                burstObservatory = burstObservatory
            )
        }

        val outbounds = buildOutboundsForTarget(
            target = node,
            settings = settings,
            resolvedHosts = resolvedHosts,
            nodeMap = nodeMap,
            visitedIds = linkedSetOf(),
            baseTag = "proxy",
            dialerProxyTag = null
        )
        return BuiltProxyPlan(outbounds = outbounds)
    }

    private fun resolvePolicyGroupMembers(
        group: ServerNode,
        nodeMap: Map<String, ServerNode>,
        allNodes: List<ServerNode>,
        visitedIds: MutableSet<String>
    ): List<ServerNode> {
        val rawSelected = mutableListOf<ServerNode>()
        group.childNodeIds.forEach { childId ->
            nodeMap[childId]?.let { rawSelected.add(it) }
        }
        if (group.policySubscriptionId.isNotBlank() && allNodes.isNotEmpty()) {
            val kw = group.policyFilter.trim()
            val subNodes = allNodes.filter { candidate ->
                candidate.id != group.id &&
                    !candidate.isGroupNode() &&
                    candidate.protocol != ProtocolType.CUSTOM_JSON &&
                    (group.policySubscriptionId == "ALL" || candidate.subscriptionId == group.policySubscriptionId) &&
                    (kw.isEmpty() || candidate.name.contains(kw, ignoreCase = true))
            }
            rawSelected.addAll(subNodes)
        }

        val resolved = linkedMapOf<String, ServerNode>()
        for (item in rawSelected) {
            if (item.protocol == ProtocolType.CUSTOM_JSON) continue
            if (item.protocol == ProtocolType.POLICY_GROUP) {
                if (visitedIds.add(item.id)) {
                    resolvePolicyGroupMembers(item, nodeMap, allNodes, visitedIds).forEach { leaf ->
                        resolved.putIfAbsent(leaf.id, leaf)
                    }
                }
            } else if (item.id !in visitedIds) {
                resolved.putIfAbsent(item.id, item)
            }
        }
        return resolved.values.toList()
    }

    private fun resolveProxyChainHops(
        chain: ServerNode,
        nodeMap: Map<String, ServerNode>,
        visitedIds: MutableSet<String>
    ): List<ServerNode> {
        val hops = mutableListOf<ServerNode>()
        for (hopId in chain.childNodeIds) {
            val hop = nodeMap[hopId] ?: continue
            if (hop.id in visitedIds || hop.protocol == ProtocolType.CUSTOM_JSON) continue
            when (hop.protocol) {
                ProtocolType.PROXY_CHAIN -> {
                    val nestedVisited = visitedIds.toMutableSet().apply { add(hop.id) }
                    hops.addAll(resolveProxyChainHops(hop, nodeMap, nestedVisited))
                }
                ProtocolType.POLICY_GROUP -> {
                    val nestedVisited = visitedIds.toMutableSet().apply { add(hop.id) }
                    resolvePolicyGroupMembers(hop, nodeMap, nodeMap.values.toList(), nestedVisited)
                        .firstOrNull()
                        ?.let { hops.add(it) }
                }
                else -> hops.add(hop)
            }
        }
        return hops
    }

    private fun buildOutboundsForTarget(
        target: ServerNode,
        settings: AppSettings,
        resolvedHosts: Map<String, List<String>>,
        nodeMap: Map<String, ServerNode>,
        visitedIds: MutableSet<String>,
        baseTag: String,
        dialerProxyTag: String?
    ): List<JsonObject> {
        return when (target.protocol) {
            ProtocolType.PROXY_CHAIN -> {
                val hops = resolveProxyChainHops(target, nodeMap, visitedIds.toMutableSet().apply { add(target.id) })
                check(hops.isNotEmpty()) { "链式代理「${target.name}」未包含任何有效链路节点" }
                if (hops.size == 1) {
                    buildOutboundsForTarget(
                        target = hops.first(),
                        settings = settings,
                        resolvedHosts = resolvedHosts,
                        nodeMap = nodeMap,
                        visitedIds = visitedIds,
                        baseTag = baseTag,
                        dialerProxyTag = dialerProxyTag
                    )
                } else {
                    val result = mutableListOf<JsonObject>()
                    // 数据包逻辑：hops[0] 最接近本机直连出网，hops.last() 为出口节点（tag=baseTag）
                    val reversed = hops.asReversed()
                    reversed.forEachIndexed { revIdx, hop ->
                        val hopTag = if (revIdx == 0) baseTag else "chain-$baseTag-$revIdx"
                        val nextHopTag = if (revIdx < reversed.lastIndex) {
                            "chain-$baseTag-${revIdx + 1}"
                        } else {
                            dialerProxyTag
                        }
                        result.addAll(
                            buildOutboundsForTarget(
                                target = hop,
                                settings = settings,
                                resolvedHosts = resolvedHosts,
                                nodeMap = nodeMap,
                                visitedIds = visitedIds,
                                baseTag = hopTag,
                                dialerProxyTag = nextHopTag
                            )
                        )
                    }
                    result
                }
            }
            ProtocolType.CUSTOM_OUTBOUND -> {
                buildCustomOutbounds(target, settings, baseTag, dialerProxyTag)
            }
            else -> {
                listOf(buildProxyOutbound(target, settings, resolvedHosts, baseTag, dialerProxyTag))
            }
        }
    }

    private fun buildCustomOutbounds(
        node: ServerNode,
        settings: AppSettings,
        baseTag: String,
        dialerProxyTag: String?
    ): List<JsonObject> {
        val rawText = node.customRawJson
            .replace("{{tag}}", baseTag)
            .replace("{{detour}}", dialerProxyTag.orEmpty())
            .trim()
        val parsed = runCatching { JsonParser.parseString(rawText) }.getOrNull()
        val result = mutableListOf<JsonObject>()

        when {
            parsed != null && parsed.isJsonArray -> {
                parsed.asJsonArray.forEach { elem ->
                    if (elem.isJsonObject) result.add(elem.asJsonObject.deepCopy())
                }
            }
            parsed != null && parsed.isJsonObject -> {
                val obj = parsed.asJsonObject
                if (obj.has("outbounds") && obj.get("outbounds").isJsonArray) {
                    obj.getAsJsonArray("outbounds").forEach { elem ->
                        if (elem.isJsonObject) result.add(elem.asJsonObject.deepCopy())
                    }
                } else {
                    result.add(obj.deepCopy())
                }
            }
        }

        if (result.isEmpty()) {
            return listOf(buildDirectOutbound(settings).apply { addProperty("tag", baseTag) })
        }

        // 首元素承担对外入站出口标签；末元素承载底层 dialerProxy 链路下一跳
        result.first().addProperty("tag", baseTag)
        if (!dialerProxyTag.isNullOrBlank()) {
            applyDialerProxy(result.last(), dialerProxyTag)
        }
        return result
    }

    private fun buildInbounds(settings: AppSettings): JsonArray {
        val listenHost = if (settings.allowLanShare) "0.0.0.0" else "127.0.0.1"
        val destOverride = JsonArray().apply {
            add("http")
            add("tls")
            add("quic")
            if (settings.enableFakeDns) add("fakedns")
        }
        val sniffing = JsonObject().apply {
            addProperty("enabled", true)
            add("destOverride", destOverride)
            addProperty("routeOnly", false)
        }

        return JsonArray().apply {
            add(JsonObject().apply {
                addProperty("tag", "socks")
                addProperty("listen", listenHost)
                addProperty("port", settings.socksPort)
                addProperty("protocol", "socks")
                add("settings", JsonObject().apply {
                    addProperty("auth", "noauth")
                    addProperty("udp", true)
                    addProperty("userLevel", 8)
                })
                add("sniffing", sniffing)
            })
            add(JsonObject().apply {
                addProperty("tag", "http")
                addProperty("listen", listenHost)
                addProperty("port", settings.httpPort)
                addProperty("protocol", "http")
                add("settings", JsonObject().apply {
                    addProperty("userLevel", 8)
                })
                add("sniffing", sniffing.deepCopy())
            })
        }
    }

    fun buildProxyOutbound(
        node: ServerNode,
        settings: AppSettings,
        resolvedHosts: Map<String, List<String>> = emptyMap(),
        tag: String = "proxy",
        dialerProxyTag: String? = null
    ): JsonObject {
        val outbound = JsonObject()
        outbound.addProperty("tag", tag)
        val allowDirectTuning = dialerProxyTag.isNullOrBlank()

        when (node.protocol) {
            ProtocolType.VLESS -> {
                outbound.addProperty("protocol", "vless")
                outbound.add("settings", JsonObject().apply {
                    addProperty("address", node.server)
                    addProperty("port", node.port)
                    addProperty("id", node.passwordOrUuid)
                    addProperty("encryption", node.encryption.ifBlank { "none" })
                    if (node.flow.isNotBlank()) addProperty("flow", node.flow)
                    addProperty("level", 8)
                })
                outbound.add("streamSettings", buildStreamSettings(node, settings, allowDirectTuning))
                applyMuxIfNeeded(outbound, node, settings)
            }
            ProtocolType.VMESS -> {
                outbound.addProperty("protocol", "vmess")
                outbound.add("settings", JsonObject().apply {
                    addProperty("address", node.server)
                    addProperty("port", node.port)
                    addProperty("id", node.passwordOrUuid)
                    addProperty("security", node.method.ifBlank { "auto" })
                    addProperty("level", 8)
                })
                outbound.add("streamSettings", buildStreamSettings(node, settings, allowDirectTuning))
                applyMuxIfNeeded(outbound, node, settings)
            }
            ProtocolType.TROJAN -> {
                outbound.addProperty("protocol", "trojan")
                outbound.add("settings", JsonObject().apply {
                    addProperty("address", node.server)
                    addProperty("port", node.port)
                    addProperty("password", node.passwordOrUuid)
                    if (node.flow.isNotBlank()) addProperty("flow", node.flow)
                    addProperty("level", 8)
                })
                outbound.add("streamSettings", buildStreamSettings(node, settings, allowDirectTuning))
            }
            ProtocolType.SHADOWSOCKS -> {
                outbound.addProperty("protocol", "shadowsocks")
                outbound.add("settings", JsonObject().apply {
                    addProperty("address", node.server)
                    addProperty("port", node.port)
                    addProperty("password", node.passwordOrUuid)
                    addProperty("method", node.method.ifBlank { "aes-128-gcm" })
                    addProperty("level", 8)
                })
                outbound.add("streamSettings", buildStreamSettings(node, settings, allowDirectTuning))
            }
            ProtocolType.HYSTERIA2 -> {
                // Xray v26.x 中 Hysteria2 使用 protocol="hysteria", settings.version=2
                outbound.addProperty("protocol", "hysteria")
                outbound.add("settings", JsonObject().apply {
                    addProperty("version", 2)
                    addProperty("address", node.server)
                    addProperty("port", node.port)
                })
                outbound.add("streamSettings", buildHysteria2StreamSettings(node))
            }
            ProtocolType.WIREGUARD -> {
                outbound.addProperty("protocol", "wireguard")
                outbound.add("settings", JsonObject().apply {
                    addProperty("secretKey", node.wgPrivateKey)
                    add("address", JsonArray().apply {
                        node.wgLocalAddress.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && (settings.enableIpv6 || !it.contains(":")) }
                            .ifEmpty { listOf("10.0.0.2/32") }
                            .forEach { add(it) }
                    })
                    add("peers", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("publicKey", node.wgPublicKey)
                            if (node.wgPresharedKey.isNotBlank()) {
                                addProperty("preSharedKey", node.wgPresharedKey)
                            }
                            val endpointHost = if (node.server.contains(":")) "[${node.server}]" else node.server
                            addProperty("endpoint", "$endpointHost:${node.port}")
                        })
                    })
                    addProperty("mtu", node.wgMtu)
                    val reservedInts = node.wgReserved.split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                    if (reservedInts.isNotEmpty()) {
                        add("reserved", JsonArray().apply { reservedInts.forEach { add(it) } })
                    }
                })
            }
            ProtocolType.MASQUE -> {
                outbound.addProperty("protocol", "masque")
                outbound.add("settings", JsonObject().apply {
                    addProperty("address", node.server)
                    addProperty("port", node.port)
                    val remoteDnsList = node.masqueRemoteDns.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (remoteDnsList.isNotEmpty()) {
                        add("remoteDNS", JsonArray().apply { remoteDnsList.forEach { add(it) } })
                    }
                })
                outbound.add("streamSettings", buildMasqueStreamSettings(node))
            }
            ProtocolType.SOCKS -> {
                outbound.addProperty("protocol", "socks")
                outbound.add("settings", JsonObject().apply {
                    addProperty("address", node.server)
                    addProperty("port", node.port)
                    if (node.username.isNotBlank()) addProperty("user", node.username)
                    if (node.passwordOrUuid.isNotBlank()) addProperty("pass", node.passwordOrUuid)
                    addProperty("level", 8)
                })
            }
            ProtocolType.HTTP -> {
                outbound.addProperty("protocol", "http")
                outbound.add("settings", JsonObject().apply {
                    addProperty("address", node.server)
                    addProperty("port", node.port)
                    if (node.username.isNotBlank()) addProperty("user", node.username)
                    if (node.passwordOrUuid.isNotBlank()) addProperty("pass", node.passwordOrUuid)
                    addProperty("level", 8)
                })
                if (node.security == SecurityMode.TLS) {
                    outbound.add("streamSettings", buildStreamSettings(node, settings, allowDirectTuning))
                }
            }
            ProtocolType.CUSTOM_JSON,
            ProtocolType.CUSTOM_OUTBOUND,
            ProtocolType.POLICY_GROUP,
            ProtocolType.PROXY_CHAIN -> {
                outbound.addProperty("protocol", "freedom")
            }
        }

        if (!dialerProxyTag.isNullOrBlank()) {
            applyDialerProxy(outbound, dialerProxyTag)
        } else if (resolvedHosts.containsKey(node.server) &&
            node.protocol != ProtocolType.CUSTOM_JSON &&
            node.protocol != ProtocolType.CUSTOM_OUTBOUND
        ) {
            val sockopt = ensureSockopt(outbound)
            sockopt.addProperty("domainStrategy", if (settings.enableIpv6) "UseIP" else "UseIPv4")
        }
        return outbound
    }

    private fun applyDialerProxy(outbound: JsonObject, dialerProxyTag: String) {
        val sockopt = ensureSockopt(outbound)
        sockopt.addProperty("dialerProxy", dialerProxyTag)
        val xhttpDownload = outbound.getAsJsonObject("streamSettings")
            ?.getAsJsonObject("xhttpSettings")
            ?.getAsJsonObject("extra")
            ?.getAsJsonObject("downloadSettings")
        if (xhttpDownload != null) {
            val dlSockopt = if (xhttpDownload.has("sockopt")) {
                xhttpDownload.getAsJsonObject("sockopt")
            } else {
                JsonObject().also { xhttpDownload.add("sockopt", it) }
            }
            dlSockopt.addProperty("dialerProxy", dialerProxyTag)
        }
    }

    private fun ensureSockopt(outbound: JsonObject): JsonObject {
        val stream = if (outbound.has("streamSettings")) {
            outbound.getAsJsonObject("streamSettings")
        } else {
            JsonObject().also { outbound.add("streamSettings", it) }
        }
        return if (stream.has("sockopt")) {
            stream.getAsJsonObject("sockopt")
        } else {
            JsonObject().also { stream.add("sockopt", it) }
        }
    }

    private fun buildStreamSettings(
        node: ServerNode,
        settings: AppSettings,
        allowFragment: Boolean = true
    ): JsonObject {
        val stream = JsonObject()
        val netName = if (node.network == TransportNetwork.RAW) "tcp" else node.network.value
        stream.addProperty("network", netName)

        when (node.network) {
            TransportNetwork.TCP, TransportNetwork.RAW -> {
                stream.add("tcpSettings", JsonObject().apply {
                    add("header", JsonObject().apply {
                        addProperty("type", node.headerType.ifBlank { "none" })
                    })
                })
            }
            TransportNetwork.WS -> {
                stream.add("wsSettings", JsonObject().apply {
                    addProperty("path", node.path.ifBlank { "/" })
                    if (node.host.isNotBlank()) {
                        addProperty("host", node.host)
                    }
                })
            }
            TransportNetwork.HTTPUPGRADE -> {
                stream.add("httpupgradeSettings", JsonObject().apply {
                    addProperty("path", node.path.ifBlank { "/" })
                    if (node.host.isNotBlank()) {
                        addProperty("host", node.host)
                    }
                })
            }
            TransportNetwork.XHTTP -> {
                stream.add("xhttpSettings", JsonObject().apply {
                    addProperty("path", node.path.ifBlank { "/" })
                    if (node.host.isNotBlank()) addProperty("host", node.host)
                    if (node.xhttpMode.isNotBlank()) addProperty("mode", node.xhttpMode)
                })
            }
            TransportNetwork.GRPC -> {
                stream.add("grpcSettings", JsonObject().apply {
                    addProperty("serviceName", node.grpcServiceName)
                    addProperty("multiMode", node.grpcMode == "multi")
                    addProperty("idle_timeout", 60)
                    addProperty("health_check_timeout", 20)
                })
            }
            else -> {}
        }

        val resolvedSni = node.sni.ifBlank {
            node.host.ifBlank { node.server }
        }

        if (node.security == SecurityMode.TLS) {
            stream.addProperty("security", "tls")
            stream.add("tlsSettings", buildTlsObject(node, resolvedSni))
        } else if (node.security == SecurityMode.REALITY) {
            stream.addProperty("security", "reality")
            stream.add("realitySettings", JsonObject().apply {
                addProperty("serverName", resolvedSni)
                addProperty("fingerprint", node.fingerprint.ifBlank { "chrome" })
                addProperty("publicKey", node.realityPublicKey)
                if (node.realityShortId.isNotBlank()) addProperty("shortId", node.realityShortId)
                if (node.realitySpiderX.isNotBlank()) addProperty("spiderX", node.realitySpiderX)
            })
        } else {
            stream.addProperty("security", "none")
        }

        // 若启用分片防阻断，为直连出口的 TLS / REALITY TCP 出站注入 finalmask.tcp fragment
        if (allowFragment &&
            settings.enableFragment &&
            (node.security == SecurityMode.TLS || node.security == SecurityMode.REALITY)
        ) {
            val packets = if (node.security == SecurityMode.REALITY) "1-3" else "tlshello"
            stream.add("finalmask", JsonObject().apply {
                add("tcp", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("type", "fragment")
                        add("settings", JsonObject().apply {
                            addProperty("packets", packets)
                            addProperty("length", "50-100")
                            addProperty("delay", "10-20")
                            addProperty("maxSplit", "10")
                        })
                    })
                })
            })
        }

        return stream
    }

    private fun buildMasqueStreamSettings(node: ServerNode): JsonObject {
        val stream = JsonObject()
        stream.addProperty("network", "masque")
        stream.addProperty("security", "tls")
        val resolvedSni = node.sni.ifBlank { node.host.ifBlank { node.server } }
        stream.add("tlsSettings", buildTlsObject(node, resolvedSni))

        val masqueSettings = JsonObject()
        if (node.masquePath.isNotBlank()) {
            masqueSettings.addProperty("path", node.masquePath.trim())
        }
        if (node.host.isNotBlank()) {
            masqueSettings.addProperty("host", node.host.trim())
        }

        val headersMap = linkedMapOf<String, String>()
        if (node.username.isNotBlank() || node.passwordOrUuid.isNotBlank()) {
            val rawCred = "${node.username}:${node.passwordOrUuid}".toByteArray(Charsets.UTF_8)
            headersMap["Authorization"] = "Basic ${Base64.getEncoder().encodeToString(rawCred)}"
        }
        node.masqueHeaders.lines().forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    // 移除同义大小写旧键避免覆盖 Authorization
                    headersMap.keys.firstOrNull { it.equals(key, ignoreCase = true) }?.let {
                        headersMap.remove(it)
                    }
                    headersMap[key] = value
                }
            }
        }
        if (headersMap.isNotEmpty()) {
            masqueSettings.add("headers", JsonObject().apply {
                headersMap.forEach { (k, v) -> addProperty(k, v) }
            })
        }

        if (node.masqueWarpPrivateKey.isNotBlank()) {
            masqueSettings.add("warp", JsonObject().apply {
                addProperty("privateKey", node.masqueWarpPrivateKey.trim())
                if (node.masqueWarpPublicKey.isNotBlank()) {
                    addProperty("publicKey", node.masqueWarpPublicKey.trim())
                }
                val addresses = node.masqueWarpAddress.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .ifEmpty { listOf("172.16.0.2/32") }
                add("address", JsonArray().apply { addresses.forEach { add(it) } })
            })
        }

        stream.add("masqueSettings", masqueSettings)
        return stream
    }

    private fun buildHysteria2StreamSettings(node: ServerNode): JsonObject {
        val stream = JsonObject()
        stream.addProperty("network", "hysteria")
        stream.add("hysteriaSettings", JsonObject().apply {
            addProperty("version", 2)
            addProperty("auth", node.passwordOrUuid)
        })
        stream.addProperty("security", "tls")
        val sni = node.sni.ifBlank { node.server }
        stream.add("tlsSettings", JsonObject().apply {
            addProperty("serverName", sni)
            addProperty("allowInsecure", node.allowInsecure && node.pinSha256.isBlank())
            add("alpn", JsonArray().apply { add("h3") })
            if (node.pinSha256.isNotBlank()) {
                addProperty("pinnedPeerCertSha256", node.pinSha256)
            }
        })

        // 构造 Salamander 混淆与端口跳跃 finalmask
        val udpMasks = JsonArray()
        if (node.hy2ObfsPassword.isNotBlank() && !node.hy2ObfsType.equals("none", ignoreCase = true)) {
            udpMasks.add(JsonObject().apply {
                addProperty("type", "salamander")
                add("settings", JsonObject().apply {
                    addProperty("password", node.hy2ObfsPassword)
                })
            })
        }
        if (node.hy2PortHopping.isNotBlank()) {
            udpMasks.add(JsonObject().apply {
                addProperty("type", "udphop")
                add("settings", JsonObject().apply {
                    addProperty("mode", "intervallocal,intervalremote")
                    addProperty("interval", "30")
                    addProperty("remotePorts", node.hy2PortHopping)
                })
            })
        }
        val quicParams = JsonObject()
        if (node.hy2UpMbps > 0) quicParams.addProperty("brutalUp", "${node.hy2UpMbps}mbps")
        if (node.hy2DownMbps > 0) quicParams.addProperty("brutalDown", "${node.hy2DownMbps}mbps")
        if (node.hy2UpMbps > 0 || node.hy2DownMbps > 0) quicParams.addProperty("congestion", "brutal")

        if (udpMasks.size() > 0 || quicParams.size() > 0) {
            stream.add("finalmask", JsonObject().apply {
                if (udpMasks.size() > 0) add("udp", udpMasks)
                if (quicParams.size() > 0) add("quicParams", quicParams)
            })
        }
        return stream
    }

    private fun buildTlsObject(node: ServerNode, sni: String): JsonObject {
        return JsonObject().apply {
            addProperty("serverName", sni)
            addProperty("allowInsecure", node.allowInsecure && node.pinSha256.isBlank())
            if (node.fingerprint.isNotBlank()) addProperty("fingerprint", node.fingerprint)
            val alpns = node.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (alpns.isNotEmpty()) {
                add("alpn", JsonArray().apply { alpns.forEach { add(it) } })
            }
            if (node.pinSha256.isNotBlank()) {
                addProperty("pinnedPeerCertSha256", node.pinSha256)
            }
        }
    }

    private fun applyMuxIfNeeded(outbound: JsonObject, node: ServerNode, settings: AppSettings) {
        val eligible = settings.enableMux &&
            node.network != TransportNetwork.XHTTP &&
            node.flow.isBlank()
        outbound.add("mux", JsonObject().apply {
            addProperty("enabled", eligible)
            addProperty("concurrency", if (eligible) 8 else -1)
        })
    }

    private fun buildDirectOutbound(settings: AppSettings): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "direct")
            addProperty("protocol", "freedom")
            add("streamSettings", JsonObject().apply {
                add("sockopt", JsonObject().apply {
                    addProperty("domainStrategy", if (settings.enableIpv6) "UseIP" else "UseIPv4")
                })
            })
        }
    }

    private fun buildBlockOutbound(): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "block")
            addProperty("protocol", "blackhole")
            add("settings", JsonObject().apply {
                add("response", JsonObject().apply { addProperty("type", "http") })
            })
        }
    }

    private fun buildDnsOutbound(): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "dns-out")
            addProperty("protocol", "dns")
        }
    }

    private fun buildDns(
        settings: AppSettings,
        resolvedHosts: Map<String, List<String>> = emptyMap()
    ): JsonObject {
        val servers = JsonArray()
        if (settings.enableFakeDns) {
            servers.add("fakedns")
        }
        val remoteDns = settings.remoteDns.ifBlank { "1.1.1.1" }
        val directDns = settings.directDns.ifBlank { "223.5.5.5" }

        if (settings.routingMode == RoutingMode.RULE_BYPASS_CN) {
            servers.add(JsonObject().apply {
                addProperty("tag", "dns-direct")
                addProperty("address", directDns)
                add("domains", JsonArray().apply { add("geosite:cn") })
                add("expectIPs", JsonArray().apply { add("geoip:cn") })
            })
            servers.add(JsonObject().apply {
                addProperty("address", remoteDns)
                add("domains", JsonArray().apply { add("geosite:geolocation-!cn") })
            })
            // 未命中上述集合的域名回落到远端 DNS
            servers.add(remoteDns)
        } else if (settings.routingMode == RoutingMode.DIRECT) {
            servers.add(directDns)
        } else {
            servers.add(remoteDns)
        }

        return JsonObject().apply {
            addProperty("tag", "dns-module")
            if (resolvedHosts.isNotEmpty()) {
                val hostsObj = JsonObject()
                resolvedHosts.forEach { (domain, ips) ->
                    if (ips.size == 1) {
                        hostsObj.addProperty(domain, ips.first())
                    } else if (ips.size > 1) {
                        hostsObj.add(domain, JsonArray().apply { ips.forEach { add(it) } })
                    }
                }
                if (hostsObj.size() > 0) {
                    add("hosts", hostsObj)
                }
            }
            add("servers", servers)
            addProperty("queryStrategy", if (settings.enableIpv6) "UseIP" else "UseIPv4")
        }
    }

    private fun buildRouting(settings: AppSettings, plan: BuiltProxyPlan): JsonObject {
        val rules = JsonArray()
        val balancerTag = plan.balancerTag

        if (settings.routingMode != RoutingMode.DIRECT) {
            // 接管来自 socks 入站的 DNS 请求交由内置 DNS 模块分流/FakeDNS 处理
            rules.add(JsonObject().apply {
                addProperty("type", "field")
                add("inboundTag", JsonArray().apply { add("socks") })
                addProperty("port", "53")
                addProperty("outboundTag", "dns-out")
            })
            if (settings.routingMode == RoutingMode.RULE_BYPASS_CN) {
                rules.add(JsonObject().apply {
                    addProperty("type", "field")
                    add("inboundTag", JsonArray().apply { add("dns-direct") })
                    addProperty("outboundTag", "direct")
                })
            }
            rules.add(JsonObject().apply {
                addProperty("type", "field")
                add("inboundTag", JsonArray().apply { add("dns-module") })
                assignProxyTarget(this, balancerTag)
            })
        }

        // 1. 用户自定义屏蔽规则
        addCustomRuleIfPresent(rules, settings.customBlockRules, "block", balancerTag = null)

        // 2. 广告屏蔽（基于 geosite:category-ads-all）
        if (settings.blockAds) {
            rules.add(JsonObject().apply {
                addProperty("type", "field")
                add("domain", JsonArray().apply { add("geosite:category-ads-all") })
                addProperty("outboundTag", "block")
            })
        }

        // 3. 用户自定义直连与代理规则
        addCustomRuleIfPresent(rules, settings.customDirectRules, "direct", balancerTag = null)
        addCustomRuleIfPresent(rules, settings.customProxyRules, "proxy", balancerTag = balancerTag)

        // 4. 分流模式主规则
        when (settings.routingMode) {
            RoutingMode.RULE_BYPASS_CN -> {
                rules.add(JsonObject().apply {
                    addProperty("type", "field")
                    add("ip", JsonArray().apply {
                        add("geoip:private")
                        add("geoip:cn")
                    })
                    addProperty("outboundTag", "direct")
                })
                rules.add(JsonObject().apply {
                    addProperty("type", "field")
                    add("domain", JsonArray().apply {
                        add("geosite:private")
                        add("geosite:cn")
                    })
                    addProperty("outboundTag", "direct")
                })
            }
            RoutingMode.GLOBAL_PROXY -> {
                rules.add(JsonObject().apply {
                    addProperty("type", "field")
                    add("ip", JsonArray().apply { add("geoip:private") })
                    addProperty("outboundTag", "direct")
                })
            }
            RoutingMode.DIRECT -> {
                rules.add(JsonObject().apply {
                    addProperty("type", "field")
                    addProperty("network", "tcp,udp")
                    addProperty("outboundTag", "direct")
                })
            }
        }

        // 5. 若启用策略组均衡器且非纯直连模式，添加兜底规则将未匹配流量全部导流到均衡器
        if (settings.routingMode != RoutingMode.DIRECT && balancerTag != null) {
            rules.add(JsonObject().apply {
                addProperty("type", "field")
                addProperty("network", "tcp,udp")
                addProperty("balancerTag", balancerTag)
            })
        }

        return JsonObject().apply {
            addProperty("domainStrategy", "IPIfNonMatch")
            if (plan.balancers.size() > 0) {
                add("balancers", plan.balancers)
            }
            add("rules", rules)
        }
    }

    private fun assignProxyTarget(ruleObj: JsonObject, balancerTag: String?) {
        if (balancerTag != null) {
            ruleObj.addProperty("balancerTag", balancerTag)
        } else {
            ruleObj.addProperty("outboundTag", "proxy")
        }
    }

    private fun addCustomRuleIfPresent(
        rules: JsonArray,
        rawText: String,
        outboundTag: String,
        balancerTag: String?
    ) {
        val items = rawText.lines()
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        if (items.isEmpty()) return

        val domains = JsonArray()
        val ips = JsonArray()
        items.forEach { item ->
            val looksLikeIpOrCidr = item.contains('/') ||
                item.startsWith("geoip:", ignoreCase = true) ||
                item.all { it.isDigit() || it == '.' || it == ':' }
            if (looksLikeIpOrCidr) ips.add(item) else domains.add(item)
        }

        if (domains.size() > 0) {
            rules.add(JsonObject().apply {
                addProperty("type", "field")
                add("domain", domains)
                if (balancerTag != null) addProperty("balancerTag", balancerTag) else addProperty("outboundTag", outboundTag)
            })
        }
        if (ips.size() > 0) {
            rules.add(JsonObject().apply {
                addProperty("type", "field")
                add("ip", ips)
                if (balancerTag != null) addProperty("balancerTag", balancerTag) else addProperty("outboundTag", outboundTag)
            })
        }
    }
}
