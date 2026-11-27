package com.iproxy.app.core.xray

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.iproxy.app.data.model.AppSettings
import com.iproxy.app.data.model.ProtocolType
import com.iproxy.app.data.model.RoutingMode
import com.iproxy.app.data.model.SecurityMode
import com.iproxy.app.data.model.ServerNode
import com.iproxy.app.data.model.TransportNetwork

/**
 * Xray-core JSON 运行时配置生成器
 *
 * 严格兼容 AndroidLibXrayLite v26.11.26 (Xray-core v26.x) 规范：
 * 支持 VLESS / VMess / Trojan / Shadowsocks / Hysteria2 / WireGuard / SOCKS / HTTP / 自定义 JSON
 */
object XrayConfigGenerator {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * 生成完整运行时 Xray JSON 配置字符串
     */
    fun generateConfig(node: ServerNode, settings: AppSettings): String {
        if (node.protocol == ProtocolType.CUSTOM_JSON && node.customRawJson.isNotBlank()) {
            val customRoot = runCatching {
                JsonParser.parseString(node.customRawJson).asJsonObject
            }.getOrNull()
            if (customRoot != null) {
                return patchCustomConfig(customRoot, settings)
            }
        }

        val root = JsonObject()

        // 1. 日志配置
        root.add("log", JsonObject().apply {
            addProperty("loglevel", "warning")
        })

        // 2. 流量统计与策略配置（驱动主页实时网速与流量面板）
        root.add("stats", JsonObject())
        root.add("policy", JsonObject().apply {
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
        })

        // 3. 入站监听（本地 SOCKS5 供 hev-socks5-tunnel 使用 + 本地 HTTP 代理）
        root.add("inbounds", buildInbounds(settings))

        // 4. 出站节点（proxy + direct + block）
        val outbounds = JsonArray()
        outbounds.add(buildProxyOutbound(node, settings))
        outbounds.add(buildDirectOutbound(settings))
        outbounds.add(buildBlockOutbound())
        root.add("outbounds", outbounds)

        // 5. DNS 与可选 FakeDNS
        root.add("dns", buildDns(settings))
        if (settings.enableFakeDns) {
            root.add("fakedns", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("ipPool", "198.18.0.0/15")
                    addProperty("poolSize", 65535)
                })
            })
        }

        // 6. 路由分流规则
        root.add("routing", buildRouting(settings))

        return gson.toJson(root)
    }

    /**
     * 生成专用于单节点真连接测速的最小 Xray 配置
     */
    fun generateSpeedTestConfig(node: ServerNode, settings: AppSettings): String {
        if (node.protocol == ProtocolType.CUSTOM_JSON && node.customRawJson.isNotBlank()) {
            val customRoot = runCatching {
                JsonParser.parseString(node.customRawJson).asJsonObject
            }.getOrNull()
            if (customRoot != null && customRoot.has("outbounds")) {
                return customRoot.toString()
            }
        }
        val root = JsonObject()
        root.add("log", JsonObject().apply { addProperty("loglevel", "none") })
        root.add("outbounds", JsonArray().apply {
            add(buildProxyOutbound(node, settings))
            add(buildDirectOutbound(settings))
        })
        return root.toString()
    }

    private fun patchCustomConfig(customRoot: JsonObject, settings: AppSettings): String {
        // 若是单个 outbound 对象而非完整配置，自动包裹进完整模板
        if (!customRoot.has("outbounds") && customRoot.has("protocol")) {
            val cloned = customRoot.deepCopy().apply { addProperty("tag", "proxy") }
            val root = JsonObject()
            root.add("log", JsonObject().apply { addProperty("loglevel", "warning") })
            root.add("stats", JsonObject())
            root.add("policy", JsonObject().apply {
                add("system", JsonObject().apply {
                    addProperty("statsOutboundUplink", true)
                    addProperty("statsOutboundDownlink", true)
                })
            })
            root.add("inbounds", buildInbounds(settings))
            root.add("outbounds", JsonArray().apply {
                add(cloned)
                add(buildDirectOutbound(settings))
                add(buildBlockOutbound())
            })
            root.add("dns", buildDns(settings))
            root.add("routing", buildRouting(settings))
            return gson.toJson(root)
        }

        if (!customRoot.has("inbounds")) {
            customRoot.add("inbounds", buildInbounds(settings))
        }
        if (!customRoot.has("stats")) {
            customRoot.add("stats", JsonObject())
        }
        return gson.toJson(customRoot)
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

    fun buildProxyOutbound(node: ServerNode, settings: AppSettings): JsonObject {
        val outbound = JsonObject()
        outbound.addProperty("tag", "proxy")

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
                outbound.add("streamSettings", buildStreamSettings(node, settings))
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
                outbound.add("streamSettings", buildStreamSettings(node, settings))
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
                outbound.add("streamSettings", buildStreamSettings(node, settings))
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
                outbound.add("streamSettings", buildStreamSettings(node, settings))
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
                    outbound.add("streamSettings", buildStreamSettings(node, settings))
                }
            }
            ProtocolType.CUSTOM_JSON -> {
                outbound.addProperty("protocol", "freedom")
            }
        }
        return outbound
    }

    private fun buildStreamSettings(node: ServerNode, settings: AppSettings): JsonObject {
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

        // 若启用分片防阻断，为 TLS / REALITY TCP 出站注入 finalmask.tcp fragment
        if (settings.enableFragment &&
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
            addProperty("allowInsecure", node.allowInsecure)
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

    private fun buildDns(settings: AppSettings): JsonObject {
        val servers = JsonArray()
        if (settings.enableFakeDns) {
            servers.add("fakedns")
        }
        val remoteDns = settings.remoteDns.ifBlank { "1.1.1.1" }
        val directDns = settings.directDns.ifBlank { "223.5.5.5" }

        if (settings.routingMode == RoutingMode.RULE_BYPASS_CN) {
            servers.add(JsonObject().apply {
                addProperty("address", directDns)
                add("domains", JsonArray().apply { add("geosite:cn") })
                add("expectIPs", JsonArray().apply { add("geoip:cn") })
            })
            servers.add(JsonObject().apply {
                addProperty("address", remoteDns)
                add("domains", JsonArray().apply { add("geosite:geolocation-!cn") })
            })
        } else if (settings.routingMode == RoutingMode.DIRECT) {
            servers.add(directDns)
        } else {
            servers.add(remoteDns)
        }

        return JsonObject().apply {
            add("servers", servers)
            addProperty("queryStrategy", if (settings.enableIpv6) "UseIP" else "UseIPv4")
        }
    }

    private fun buildRouting(settings: AppSettings): JsonObject {
        val rules = JsonArray()

        // 1. 用户自定义屏蔽规则
        addCustomRuleIfPresent(rules, settings.customBlockRules, "block")

        // 2. 广告屏蔽（基于 geosite:category-ads-all）
        if (settings.blockAds) {
            rules.add(JsonObject().apply {
                addProperty("type", "field")
                add("domain", JsonArray().apply { add("geosite:category-ads-all") })
                addProperty("outboundTag", "block")
            })
        }

        // 3. 用户自定义直连与代理规则
        addCustomRuleIfPresent(rules, settings.customDirectRules, "direct")
        addCustomRuleIfPresent(rules, settings.customProxyRules, "proxy")

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

        return JsonObject().apply {
            addProperty("domainStrategy", "IPIfNonMatch")
            add("rules", rules)
        }
    }

    private fun addCustomRuleIfPresent(
        rules: JsonArray,
        rawText: String,
        outboundTag: String
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
                addProperty("outboundTag", outboundTag)
            })
        }
        if (ips.size() > 0) {
            rules.add(JsonObject().apply {
                addProperty("type", "field")
                add("ip", ips)
                addProperty("outboundTag", outboundTag)
            })
        }
    }
}
