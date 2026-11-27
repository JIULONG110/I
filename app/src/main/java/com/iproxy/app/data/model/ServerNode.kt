package com.iproxy.app.data.model

import java.util.UUID

/**
 * 支持的代理协议类型
 */
enum class ProtocolType(val displayName: String, val scheme: String) {
    VLESS("VLESS", "vless"),
    VMESS("VMess", "vmess"),
    TROJAN("Trojan", "trojan"),
    SHADOWSOCKS("Shadowsocks", "ss"),
    HYSTERIA2("Hysteria2", "hysteria2"),
    WIREGUARD("WireGuard", "wireguard"),
    SOCKS("SOCKS5", "socks"),
    HTTP("HTTP", "http"),
    CUSTOM_JSON("自定义JSON", "json")
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
    HYSTERIA("hysteria");

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
 * 节点配置统一模型，兼容主流协议分享链接、订阅与自定义 JSON
 */
data class ServerNode(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: ProtocolType,
    val server: String,
    val port: Int,
    val subscriptionId: String? = null,
    // 认证与密码字段（VLESS/VMess UUID、Trojan/SS/Hy2 密码等）
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
    // 自定义完整 Xray JSON（当 protocol == CUSTOM_JSON 时使用）
    val customRawJson: String = "",
    // 运行态元数据
    val latencyMs: Long? = null,
    val lastTestedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * 用于页面展示的协议与安全标签
     */
    fun badgeText(): String = buildString {
        append(protocol.displayName)
        if (security == SecurityMode.REALITY) {
            append("·REALITY")
        } else if (security == SecurityMode.TLS && protocol != ProtocolType.HYSTERIA2) {
            append("·TLS")
        }
    }
}
