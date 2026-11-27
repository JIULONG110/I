package com.iproxy.app.data.model

import java.util.UUID

/**
 * 支持的代理协议与组合配置类型
 */
enum class ProtocolType(val displayName: String, val scheme: String) {
    VLESS("VLESS", "vless"),
    VMESS("VMess", "vmess"),
    TROJAN("Trojan", "trojan"),
    SHADOWSOCKS("Shadowsocks", "ss"),
    HYSTERIA2("Hysteria2", "hysteria2"),
    WIREGUARD("WireGuard", "wireguard"),
    MASQUE("MASQUE", "masque"),
    SOCKS("SOCKS", "socks"),
    HTTP("HTTP", "http"),
    POLICY_GROUP("策略组", "policygroup"),
    PROXY_CHAIN("链式代理", "proxychain"),
    CUSTOM_OUTBOUND("自定义出站", "outbound"),
    CUSTOM_JSON("自定义配置", "json")
}

/**
 * 策略组调度策略
 */
enum class PolicyStrategy(val value: String, val displayName: String) {
    LEAST_PING("leastPing", "最低延迟"),
    FALLBACK("fallback", "故障转移"),
    LEAST_LOAD("leastLoad", "负载均衡"),
    ROUND_ROBIN("roundRobin", "轮询"),
    RANDOM("random", "随机")
}

/**
 * 传输层网络类型
 */
enum class TransportNetwork(val value: String) {
    TCP("tcp"),
    RAW("raw"),
    WS("ws"),
    GRPC("grpc"),
    XHTTP("xhttp"),
    HTTPUPGRADE("httpupgrade"),
    QUIC("quic"),
    HYSTERIA("hysteria"),
    MASQUE("masque");

    companion object {
        fun fromString(raw: String?): TransportNetwork {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.value == normalized } ?: when (normalized) {
                "splithttp" -> XHTTP
                "kcp", "mkcp" -> RAW
                else -> TCP
            }
        }
    }
}

/**
 * 传输层安全模式
 */
enum class SecurityMode(val value: String) {
    NONE("none"),
    TLS("tls"),
    REALITY("reality");

    companion object {
        fun fromString(raw: String?): SecurityMode {
            return when (raw?.trim()?.lowercase()) {
                "tls" -> TLS
                "reality" -> REALITY
                else -> NONE
            }
        }
    }
}

/**
 * 节点配置统一模型，兼容主流协议分享链接、订阅、策略组、链式代理与自定义配置
 */
data class ServerNode(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: ProtocolType,
    val server: String,
    val port: Int,
    val subscriptionId: String? = null,
    // 认证与密码字段（VLESS/VMess UUID、Trojan/SS/Hy2/SOCKS/HTTP/MASQUE 密码等）
    val passwordOrUuid: String = "",
    val username: String = "",
    val method: String = "aes-128-gcm",
    val alterId: Int = 0,
    val flow: String = "",
    val encryption: String = "none",
    // 传输层参数
    val network: TransportNetwork = TransportNetwork.TCP,
    val headerType: String = "none",
    val host: String = "",
    val path: String = "",
    val xhttpMode: String = "",
    val grpcServiceName: String = "",
    val grpcMode: String = "gun",
    // TLS / REALITY 参数
    val security: SecurityMode = SecurityMode.NONE,
    val sni: String = "",
    val alpn: String = "",
    val fingerprint: String = "",
    val allowInsecure: Boolean = false,
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val realitySpiderX: String = "",
    // Hysteria2 专用参数
    val hy2ObfsType: String = "",
    val hy2ObfsPassword: String = "",
    val hy2PortHopping: String = "",
    val hy2UpMbps: Int = 0,
    val hy2DownMbps: Int = 0,
    val pinSha256: String = "",
    // WireGuard 专用参数
    val wgPrivateKey: String = "",
    val wgPublicKey: String = "",
    val wgPresharedKey: String = "",
    val wgLocalAddress: String = "10.0.0.2/32",
    val wgReserved: String = "",
    val wgMtu: Int = 1420,
    // MASQUE 专用参数
    val masquePath: String = "",
    val masqueHeaders: String = "",
    val masqueRemoteDns: String = "",
    val masqueWarpPrivateKey: String = "",
    val masqueWarpPublicKey: String = "",
    val masqueWarpAddress: String = "",
    // 策略组与链式代理参数
    val policyStrategy: PolicyStrategy = PolicyStrategy.LEAST_PING,
    val childNodeIds: List<String> = emptyList(),
    val policySubscriptionId: String = "",
    val policyFilter: String = "",
    // 自定义配置 / 自定义出站 Xray JSON
    val customRawJson: String = "",
    // 运行态元数据
    val latencyMs: Long? = null,
    val lastTestedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * 是否为组合型节点（策略组或链式代理）
     */
    fun isGroupNode(): Boolean =
        protocol == ProtocolType.POLICY_GROUP || protocol == ProtocolType.PROXY_CHAIN

    /**
     * 用于页面展示的协议与安全标签
     */
    fun badgeText(): String = when (protocol) {
        ProtocolType.POLICY_GROUP -> "策略组·${safePolicyStrategy().displayName}"
        ProtocolType.PROXY_CHAIN -> "链式代理·${safeChildNodeIds().size}跳"
        ProtocolType.CUSTOM_OUTBOUND -> "自定义出站"
        ProtocolType.CUSTOM_JSON -> "自定义配置"
        ProtocolType.MASQUE -> if (safeStr(masqueWarpPrivateKey).isNotBlank()) "MASQUE·WARP" else "MASQUE·TLS"
        else -> buildString {
            append(protocol.displayName)
            if (security == SecurityMode.REALITY) {
                append("·REALITY")
            } else if (security == SecurityMode.TLS && protocol != ProtocolType.HYSTERIA2) {
                append("·TLS")
            }
        }
    }

    /**
     * 节点列表与首页展示的副标题（替代生硬的 custom:443）
     */
    fun displayAddressSummary(): String = when (protocol) {
        ProtocolType.POLICY_GROUP -> {
            val childCount = safeChildNodeIds().size
            val subPart = if (safeStr(policySubscriptionId).isNotBlank()) "订阅动态筛选" else ""
            when {
                childCount > 0 && subPart.isNotEmpty() -> "$childCount 个固定节点 + $subPart"
                childCount > 0 -> "包含 $childCount 个节点"
                subPart.isNotEmpty() -> subPart
                else -> "暂未选择子节点"
            }
        }
        ProtocolType.PROXY_CHAIN -> {
            val hopCount = safeChildNodeIds().size
            if (hopCount > 0) "共 $hopCount 跳中转链路" else "暂未配置链路节点"
        }
        ProtocolType.CUSTOM_OUTBOUND -> "Xray Outbound JSON"
        ProtocolType.CUSTOM_JSON -> "完整 Xray 配置 JSON"
        else -> "$server:$port"
    }

    /**
     * 兼容旧版本 Gson 反序列化缺失字段时 Unsafe 置 null 的安全清洗
     */
    fun sanitized(): ServerNode = copy(
        passwordOrUuid = safeStr(passwordOrUuid),
        username = safeStr(username),
        method = safeStr(method, "aes-128-gcm"),
        flow = safeStr(flow),
        encryption = safeStr(encryption, "none"),
        network = safeObj(network, TransportNetwork.TCP),
        headerType = safeStr(headerType, "none"),
        host = safeStr(host),
        path = safeStr(path),
        xhttpMode = safeStr(xhttpMode),
        grpcServiceName = safeStr(grpcServiceName),
        grpcMode = safeStr(grpcMode, "gun"),
        security = safeObj(security, SecurityMode.NONE),
        sni = safeStr(sni),
        alpn = safeStr(alpn),
        fingerprint = safeStr(fingerprint),
        realityPublicKey = safeStr(realityPublicKey),
        realityShortId = safeStr(realityShortId),
        realitySpiderX = safeStr(realitySpiderX),
        hy2ObfsType = safeStr(hy2ObfsType),
        hy2ObfsPassword = safeStr(hy2ObfsPassword),
        hy2PortHopping = safeStr(hy2PortHopping),
        pinSha256 = safeStr(pinSha256),
        wgPrivateKey = safeStr(wgPrivateKey),
        wgPublicKey = safeStr(wgPublicKey),
        wgPresharedKey = safeStr(wgPresharedKey),
        wgLocalAddress = safeStr(wgLocalAddress, "10.0.0.2/32"),
        wgReserved = safeStr(wgReserved),
        masquePath = safeStr(masquePath),
        masqueHeaders = safeStr(masqueHeaders),
        masqueRemoteDns = safeStr(masqueRemoteDns),
        masqueWarpPrivateKey = safeStr(masqueWarpPrivateKey),
        masqueWarpPublicKey = safeStr(masqueWarpPublicKey),
        masqueWarpAddress = safeStr(masqueWarpAddress),
        policyStrategy = safePolicyStrategy(),
        childNodeIds = safeChildNodeIds(),
        policySubscriptionId = safeStr(policySubscriptionId),
        policyFilter = safeStr(policyFilter),
        customRawJson = safeStr(customRawJson)
    )

    @Suppress("USELESS_ELVIS")
    private fun safePolicyStrategy(): PolicyStrategy = (policyStrategy as PolicyStrategy?) ?: PolicyStrategy.LEAST_PING

    @Suppress("USELESS_ELVIS")
    private fun safeChildNodeIds(): List<String> = (childNodeIds as List<String>?) ?: emptyList()

    companion object {
        @Suppress("USELESS_ELVIS")
        private fun safeStr(value: String?, default: String = ""): String = value ?: default

        @Suppress("USELESS_ELVIS")
        private fun <T> safeObj(value: T?, default: T): T = value ?: default
    }
}
