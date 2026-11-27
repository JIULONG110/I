package com.iproxy.app.core.parser

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.iproxy.app.core.util.FormatUtils
import com.iproxy.app.data.model.ProtocolType
import com.iproxy.app.data.model.SecurityMode
import com.iproxy.app.data.model.ServerNode
import com.iproxy.app.data.model.TransportNetwork
import java.net.URI

/**
 * 主流代理协议分享链接解析与生成引擎
 *
 * 支持协议：VLESS / VMess / Trojan / Shadowsocks / Hysteria2 / WireGuard / SOCKS5 / HTTP / 自定义 Xray JSON
 */
object ShareLinkParser {

    /**
     * 解析单条分享链接或 JSON 配置为 [ServerNode]，失败返回 null
     */
    fun parse(rawInput: String, subscriptionId: String? = null): ServerNode? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        // 若是 JSON 文本，直接作为自定义 Xray JSON 节点解析
        if (trimmed.startsWith("{")) {
            return parseCustomJson(trimmed, subscriptionId)
        }

        val lower = trimmed.lowercase()
        return when {
            lower.startsWith("vless://") -> parseVless(trimmed, subscriptionId)
            lower.startsWith("vmess://") -> parseVmess(trimmed, subscriptionId)
            lower.startsWith("trojan://") -> parseTrojan(trimmed, subscriptionId)
            lower.startsWith("ss://") -> parseShadowsocks(trimmed, subscriptionId)
            lower.startsWith("hysteria2://") || lower.startsWith("hy2://") ->
                parseHysteria2(trimmed, subscriptionId)
            lower.startsWith("wireguard://") || lower.startsWith("wg://") ->
                parseWireGuard(trimmed, subscriptionId)
            lower.startsWith("socks://") || lower.startsWith("socks5://") ->
                parseSocks(trimmed, subscriptionId)
            lower.startsWith("http://") || lower.startsWith("https://") ->
                parseHttpProxy(trimmed, subscriptionId)
            else -> null
        }
    }

    /**
     * 将 [ServerNode] 导出为标准分享链接，便于二维码分享与复制
     */
    fun toShareLink(node: ServerNode): String {
        return when (node.protocol) {
            ProtocolType.VLESS -> buildVlessLink(node)
            ProtocolType.VMESS -> buildVmessLink(node)
            ProtocolType.TROJAN -> buildTrojanLink(node)
            ProtocolType.SHADOWSOCKS -> buildShadowsocksLink(node)
            ProtocolType.HYSTERIA2 -> buildHysteria2Link(node)
            ProtocolType.WIREGUARD -> buildWireGuardLink(node)
            ProtocolType.SOCKS -> buildSocksLink(node)
            ProtocolType.HTTP -> buildHttpLink(node)
            ProtocolType.CUSTOM_JSON -> node.customRawJson
        }
    }

    private fun parseVless(link: String, subscriptionId: String?): ServerNode? {
        val uri = safeParseUri(link) ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port.takeIf { it in 1..65535 } ?: return null
        val uuid = FormatUtils.urlDecode(uri.userInfo.orEmpty())
        if (uuid.isBlank()) return null

        val params = parseQueryParams(uri.rawQuery)
        val remark = extractRemark(uri, "${host}:${port}")
        val network = TransportNetwork.fromString(params["type"])
        val security = SecurityMode.fromString(params["security"])

        return ServerNode(
            name = remark,
            protocol = ProtocolType.VLESS,
            server = host,
            port = port,
            subscriptionId = subscriptionId,
            passwordOrUuid = uuid,
            flow = params["flow"].orEmpty(),
            encryption = params["encryption"].orEmpty().ifBlank { "none" },
            network = network,
            headerType = params["headerType"].orEmpty().ifBlank { "none" },
            host = params["host"].orEmpty(),
            path = params["path"].orEmpty(),
            xhttpMode = params["mode"].orEmpty(),
            grpcServiceName = params["serviceName"].orEmpty(),
            grpcMode = params["mode"].orEmpty().ifBlank { "gun" },
            security = security,
            sni = params["sni"].orEmpty(),
            alpn = params["alpn"].orEmpty(),
            fingerprint = params["fp"].orEmpty(),
            allowInsecure = params["allowInsecure"] == "1" || params["insecure"] == "1",
            realityPublicKey = params["pbk"].orEmpty(),
            realityShortId = params["sid"].orEmpty(),
            realitySpiderX = params["spx"].orEmpty()
        )
    }

    private fun parseVmess(link: String, subscriptionId: String?): ServerNode? {
        val payload = link.substringAfter("://").trim()
        // 1. 尝试标准 v2rayN Base64 JSON 格式
        val decoded = FormatUtils.decodeBase64Safe(payload)
        if (decoded != null && decoded.trimStart().startsWith("{")) {
            val obj = runCatching { JsonParser.parseString(decoded).asJsonObject }.getOrNull()
            if (obj != null) {
                val host = obj.getStringOrEmpty("add")
                val port = obj.getIntOrDefault("port", 0)
                val uuid = obj.getStringOrEmpty("id")
                if (host.isNotBlank() && port in 1..65535 && uuid.isNotBlank()) {
                    val net = TransportNetwork.fromString(obj.getStringOrEmpty("net"))
                    val tlsStr = obj.getStringOrEmpty("tls")
                    val security = if (tlsStr.equals("tls", ignoreCase = true)) {
                        SecurityMode.TLS
                    } else {
                        SecurityMode.fromString(tlsStr)
                    }
                    val ps = obj.getStringOrEmpty("ps").ifBlank { "$host:$port" }
                    return ServerNode(
                        name = ps,
                        protocol = ProtocolType.VMESS,
                        server = host,
                        port = port,
                        subscriptionId = subscriptionId,
                        passwordOrUuid = uuid,
                        alterId = obj.getIntOrDefault("aid", 0),
                        method = obj.getStringOrEmpty("scy").ifBlank { "auto" },
                        network = net,
                        headerType = obj.getStringOrEmpty("type").ifBlank { "none" },
                        host = obj.getStringOrEmpty("host"),
                        path = obj.getStringOrEmpty("path"),
                        grpcServiceName = if (net == TransportNetwork.GRPC) obj.getStringOrEmpty("path") else "",
                        security = security,
                        sni = obj.getStringOrEmpty("sni"),
                        alpn = obj.getStringOrEmpty("alpn"),
                        fingerprint = obj.getStringOrEmpty("fp"),
                        allowInsecure = obj.getStringOrEmpty("insecure") == "1"
                    )
                }
            }
        }

        // 2. 兼容 vmess://uuid@host:port?... 标准 URI 格式
        val uri = safeParseUri(link) ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port.takeIf { it in 1..65535 } ?: return null
        val uuid = FormatUtils.urlDecode(uri.userInfo.orEmpty())
        if (uuid.isBlank()) return null
        val params = parseQueryParams(uri.rawQuery)
        val remark = extractRemark(uri, "$host:$port")
        val net = TransportNetwork.fromString(params["type"])
        return ServerNode(
            name = remark,
            protocol = ProtocolType.VMESS,
            server = host,
            port = port,
            subscriptionId = subscriptionId,
            passwordOrUuid = uuid,
            method = params["encryption"].orEmpty().ifBlank { "auto" },
            network = net,
            headerType = params["headerType"].orEmpty().ifBlank { "none" },
            host = params["host"].orEmpty(),
            path = params["path"].orEmpty(),
            grpcServiceName = params["serviceName"].orEmpty(),
            security = SecurityMode.fromString(params["security"]),
            sni = params["sni"].orEmpty(),
            alpn = params["alpn"].orEmpty(),
            fingerprint = params["fp"].orEmpty()
        )
    }

    private fun parseTrojan(link: String, subscriptionId: String?): ServerNode? {
        val uri = safeParseUri(link) ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port.takeIf { it in 1..65535 } ?: 443
        val password = FormatUtils.urlDecode(uri.userInfo.orEmpty())
        if (password.isBlank()) return null

        val params = parseQueryParams(uri.rawQuery)
        val remark = extractRemark(uri, "$host:$port")
        val security = SecurityMode.fromString(params["security"] ?: "tls")
        return ServerNode(
            name = remark,
            protocol = ProtocolType.TROJAN,
            server = host,
            port = port,
            subscriptionId = subscriptionId,
            passwordOrUuid = password,
            network = TransportNetwork.fromString(params["type"]),
            headerType = params["headerType"].orEmpty().ifBlank { "none" },
            host = params["host"].orEmpty(),
            path = params["path"].orEmpty(),
            grpcServiceName = params["serviceName"].orEmpty(),
            security = security,
            sni = params["sni"].orEmpty().ifBlank { params["peer"].orEmpty() },
            alpn = params["alpn"].orEmpty(),
            fingerprint = params["fp"].orEmpty(),
            allowInsecure = params["allowInsecure"] == "1" || params["insecure"] == "1",
            realityPublicKey = params["pbk"].orEmpty(),
            realityShortId = params["sid"].orEmpty()
        )
    }

    private fun parseShadowsocks(link: String, subscriptionId: String?): ServerNode? {
        val withoutScheme = link.substringAfter("://")
        val remarkPart = withoutScheme.substringAfter("#", "")
        val remark = if (remarkPart.isNotEmpty()) FormatUtils.urlDecode(remarkPart) else ""
        val mainAndQuery = withoutScheme.substringBefore("#")
        val mainPart = mainAndQuery.substringBefore("?")

        // 情况 1: SIP002 ss://userinfo@host:port 或 ss://method:password@host:port
        if (mainPart.contains("@")) {
            val userPart = mainPart.substringBeforeLast("@")
            val hostPortPart = mainPart.substringAfterLast("@")
            val (host, port) = splitHostPort(hostPortPart) ?: return null

            val decodedUser = if (userPart.contains(":")) {
                FormatUtils.urlDecode(userPart)
            } else {
                FormatUtils.decodeBase64Safe(userPart) ?: return null
            }
            val colonIdx = decodedUser.indexOf(':')
            if (colonIdx <= 0) return null
            val method = decodedUser.substring(0, colonIdx)
            val password = decodedUser.substring(colonIdx + 1)

            return ServerNode(
                name = remark.ifBlank { "$host:$port" },
                protocol = ProtocolType.SHADOWSOCKS,
                server = host,
                port = port,
                subscriptionId = subscriptionId,
                passwordOrUuid = password,
                method = method
            )
        }

        // 情况 2: 传统整体 Base64 ss://base64(method:password@host:port)#remark
        val decodedFull = FormatUtils.decodeBase64Safe(mainPart) ?: return null
        val atIdx = decodedFull.lastIndexOf('@')
        if (atIdx <= 0) return null
        val cred = decodedFull.substring(0, atIdx)
        val hostPort = decodedFull.substring(atIdx + 1)
        val (host, port) = splitHostPort(hostPort) ?: return null
        val colonIdx = cred.indexOf(':')
        if (colonIdx <= 0) return null

        return ServerNode(
            name = remark.ifBlank { "$host:$port" },
            protocol = ProtocolType.SHADOWSOCKS,
            server = host,
            port = port,
            subscriptionId = subscriptionId,
            passwordOrUuid = cred.substring(colonIdx + 1),
            method = cred.substring(0, colonIdx)
        )
    }

    private fun parseHysteria2(link: String, subscriptionId: String?): ServerNode? {
        // 将 hy2:// 规范为 hysteria2:// 以便 URI 解析
        val normalized = if (link.lowercase().startsWith("hy2://")) {
            "hysteria2://" + link.substring(6)
        } else {
            link
        }
        val uri = safeParseUri(normalized) ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port.takeIf { it in 1..65535 } ?: 443
        val auth = FormatUtils.urlDecode(uri.userInfo.orEmpty())
        val params = parseQueryParams(uri.rawQuery)
        val remark = extractRemark(uri, "$host:$port")

        return ServerNode(
            name = remark,
            protocol = ProtocolType.HYSTERIA2,
            server = host,
            port = port,
            subscriptionId = subscriptionId,
            passwordOrUuid = auth,
            network = TransportNetwork.HYSTERIA,
            security = SecurityMode.TLS,
            sni = params["sni"].orEmpty(),
            alpn = params["alpn"].orEmpty(),
            allowInsecure = params["insecure"] == "1" || params["insecure"] == "true",
            hy2ObfsType = params["obfs"].orEmpty(),
            hy2ObfsPassword = params["obfs-password"].orEmpty(),
            hy2PortHopping = params["mport"].orEmpty(),
            hy2UpMbps = params["upmbps"]?.toIntOrNull() ?: 0,
            hy2DownMbps = params["downmbps"]?.toIntOrNull() ?: 0,
            pinSha256 = params["pinSHA256"].orEmpty()
        )
    }

    private fun parseWireGuard(link: String, subscriptionId: String?): ServerNode? {
        val normalized = if (link.lowercase().startsWith("wg://")) {
            "wireguard://" + link.substring(5)
        } else {
            link
        }
        val uri = safeParseUri(normalized) ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port.takeIf { it in 1..65535 } ?: 51820
        val privateKey = FormatUtils.urlDecode(uri.userInfo.orEmpty())
        val params = parseQueryParams(uri.rawQuery)
        val remark = extractRemark(uri, "$host:$port")

        return ServerNode(
            name = remark,
            protocol = ProtocolType.WIREGUARD,
            server = host,
            port = port,
            subscriptionId = subscriptionId,
            wgPrivateKey = privateKey,
            wgPublicKey = params["publickey"].orEmpty(),
            wgPresharedKey = params["presharedkey"].orEmpty(),
            wgLocalAddress = params["address"].orEmpty().ifBlank { "10.0.0.2/32" },
            wgReserved = params["reserved"].orEmpty(),
            wgMtu = params["mtu"]?.toIntOrNull() ?: 1420
        )
    }

    private fun parseSocks(link: String, subscriptionId: String?): ServerNode? {
        val uri = safeParseUri(link) ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port.takeIf { it in 1..65535 } ?: 1080
        val remark = extractRemark(uri, "$host:$port")
        var user = ""
        var pass = ""
        val rawUserInfo = uri.userInfo.orEmpty()
        if (rawUserInfo.isNotEmpty()) {
            val decoded = if (rawUserInfo.contains(":")) rawUserInfo
            else FormatUtils.decodeBase64Safe(rawUserInfo) ?: rawUserInfo
            val idx = decoded.indexOf(':')
            if (idx >= 0) {
                user = FormatUtils.urlDecode(decoded.substring(0, idx))
                pass = FormatUtils.urlDecode(decoded.substring(idx + 1))
            } else {
                user = FormatUtils.urlDecode(decoded)
            }
        }
        return ServerNode(
            name = remark,
            protocol = ProtocolType.SOCKS,
            server = host,
            port = port,
            subscriptionId = subscriptionId,
            username = user,
            passwordOrUuid = pass
        )
    }

    private fun parseHttpProxy(link: String, subscriptionId: String?): ServerNode? {
        val uri = safeParseUri(link) ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val isHttps = link.lowercase().startsWith("https://")
        val port = uri.port.takeIf { it in 1..65535 } ?: if (isHttps) 443 else 8080
        val remark = extractRemark(uri, "$host:$port")
        var user = ""
        var pass = ""
        val rawUserInfo = uri.userInfo.orEmpty()
        if (rawUserInfo.contains(":")) {
            val idx = rawUserInfo.indexOf(':')
            user = FormatUtils.urlDecode(rawUserInfo.substring(0, idx))
            pass = FormatUtils.urlDecode(rawUserInfo.substring(idx + 1))
        }
        return ServerNode(
            name = remark,
            protocol = ProtocolType.HTTP,
            server = host,
            port = port,
            subscriptionId = subscriptionId,
            username = user,
            passwordOrUuid = pass,
            security = if (isHttps) SecurityMode.TLS else SecurityMode.NONE
        )
    }

    private fun parseCustomJson(jsonText: String, subscriptionId: String?): ServerNode? {
        val obj = runCatching { JsonParser.parseString(jsonText).asJsonObject }.getOrNull() ?: return null
        // 校验至少包含 outbounds 字段或 protocol 字段
        val hasOutbounds = obj.has("outbounds") && obj.get("outbounds").isJsonArray
        val hasProtocol = obj.has("protocol")
        if (!hasOutbounds && !hasProtocol) return null

        val remarks = obj.getStringOrEmpty("remarks").ifBlank {
            obj.getStringOrEmpty("ps").ifBlank { "自定义 Xray 节点" }
        }
        return ServerNode(
            name = remarks,
            protocol = ProtocolType.CUSTOM_JSON,
            server = "custom",
            port = 443,
            subscriptionId = subscriptionId,
            customRawJson = jsonText
        )
    }

    // region 分享链接生成
    private fun buildVlessLink(node: ServerNode): String {
        val query = linkedMapOf<String, String>()
        query["type"] = node.network.value
        query["encryption"] = node.encryption.ifBlank { "none" }
        if (node.security != SecurityMode.NONE) query["security"] = node.security.value
        if (node.flow.isNotBlank()) query["flow"] = node.flow
        if (node.sni.isNotBlank()) query["sni"] = node.sni
        if (node.alpn.isNotBlank()) query["alpn"] = node.alpn
        if (node.fingerprint.isNotBlank()) query["fp"] = node.fingerprint
        if (node.allowInsecure) query["allowInsecure"] = "1"
        if (node.host.isNotBlank()) query["host"] = node.host
        if (node.path.isNotBlank()) query["path"] = node.path
        if (node.network == TransportNetwork.XHTTP && node.xhttpMode.isNotBlank()) {
            query["mode"] = node.xhttpMode
        }
        if (node.network == TransportNetwork.GRPC && node.grpcServiceName.isNotBlank()) {
            query["serviceName"] = node.grpcServiceName
            query["mode"] = node.grpcMode
        }
        if (node.security == SecurityMode.REALITY) {
            if (node.realityPublicKey.isNotBlank()) query["pbk"] = node.realityPublicKey
            if (node.realityShortId.isNotBlank()) query["sid"] = node.realityShortId
            if (node.realitySpiderX.isNotBlank()) query["spx"] = node.realitySpiderX
        }
        val queryStr = encodeQuery(query)
        val hostPort = FormatUtils.formatHostPort(node.server, node.port)
        return "vless://${FormatUtils.urlEncode(node.passwordOrUuid)}@$hostPort?$queryStr#${FormatUtils.urlEncode(node.name)}"
    }

    private fun buildVmessLink(node: ServerNode): String {
        val obj = JsonObject().apply {
            addProperty("v", "2")
            addProperty("ps", node.name)
            addProperty("add", node.server)
            addProperty("port", node.port.toString())
            addProperty("id", node.passwordOrUuid)
            addProperty("aid", node.alterId.toString())
            addProperty("scy", node.method.ifBlank { "auto" })
            addProperty("net", node.network.value)
            addProperty("type", node.headerType.ifBlank { "none" })
            addProperty("host", node.host)
            addProperty("path", if (node.network == TransportNetwork.GRPC) node.grpcServiceName else node.path)
            addProperty("tls", if (node.security == SecurityMode.TLS) "tls" else "")
            addProperty("sni", node.sni)
            addProperty("alpn", node.alpn)
            addProperty("fp", node.fingerprint)
        }
        return "vmess://${FormatUtils.encodeBase64(obj.toString())}"
    }

    private fun buildTrojanLink(node: ServerNode): String {
        val query = linkedMapOf<String, String>()
        query["type"] = node.network.value
        query["security"] = node.security.value
        if (node.sni.isNotBlank()) query["sni"] = node.sni
        if (node.alpn.isNotBlank()) query["alpn"] = node.alpn
        if (node.fingerprint.isNotBlank()) query["fp"] = node.fingerprint
        if (node.allowInsecure) query["allowInsecure"] = "1"
        if (node.host.isNotBlank()) query["host"] = node.host
        if (node.path.isNotBlank()) query["path"] = node.path
        val hostPort = FormatUtils.formatHostPort(node.server, node.port)
        return "trojan://${FormatUtils.urlEncode(node.passwordOrUuid)}@$hostPort?${encodeQuery(query)}#${FormatUtils.urlEncode(node.name)}"
    }

    private fun buildShadowsocksLink(node: ServerNode): String {
        val userInfo = FormatUtils.encodeBase64UrlNoPad("${node.method}:${node.passwordOrUuid}")
        val hostPort = FormatUtils.formatHostPort(node.server, node.port)
        return "ss://$userInfo@$hostPort#${FormatUtils.urlEncode(node.name)}"
    }

    private fun buildHysteria2Link(node: ServerNode): String {
        val query = linkedMapOf<String, String>()
        if (node.sni.isNotBlank()) query["sni"] = node.sni
        if (node.allowInsecure) query["insecure"] = "1"
        if (node.hy2ObfsType.isNotBlank()) query["obfs"] = node.hy2ObfsType
        if (node.hy2ObfsPassword.isNotBlank()) query["obfs-password"] = node.hy2ObfsPassword
        if (node.hy2PortHopping.isNotBlank()) query["mport"] = node.hy2PortHopping
        if (node.pinSha256.isNotBlank()) query["pinSHA256"] = node.pinSha256
        val queryPart = if (query.isNotEmpty()) "?${encodeQuery(query)}" else ""
        val hostPort = FormatUtils.formatHostPort(node.server, node.port)
        return "hysteria2://${FormatUtils.urlEncode(node.passwordOrUuid)}@$hostPort$queryPart#${FormatUtils.urlEncode(node.name)}"
    }

    private fun buildWireGuardLink(node: ServerNode): String {
        val query = linkedMapOf<String, String>()
        if (node.wgPublicKey.isNotBlank()) query["publickey"] = node.wgPublicKey
        if (node.wgPresharedKey.isNotBlank()) query["presharedkey"] = node.wgPresharedKey
        if (node.wgLocalAddress.isNotBlank()) query["address"] = node.wgLocalAddress
        if (node.wgReserved.isNotBlank()) query["reserved"] = node.wgReserved
        query["mtu"] = node.wgMtu.toString()
        val hostPort = FormatUtils.formatHostPort(node.server, node.port)
        return "wireguard://${FormatUtils.urlEncode(node.wgPrivateKey)}@$hostPort?${encodeQuery(query)}#${FormatUtils.urlEncode(node.name)}"
    }

    private fun buildSocksLink(node: ServerNode): String {
        val auth = if (node.username.isNotBlank() || node.passwordOrUuid.isNotBlank()) {
            "${FormatUtils.urlEncode(node.username)}:${FormatUtils.urlEncode(node.passwordOrUuid)}@"
        } else ""
        val hostPort = FormatUtils.formatHostPort(node.server, node.port)
        return "socks://$auth$hostPort#${FormatUtils.urlEncode(node.name)}"
    }

    private fun buildHttpLink(node: ServerNode): String {
        val scheme = if (node.security == SecurityMode.TLS) "https" else "http"
        val auth = if (node.username.isNotBlank() || node.passwordOrUuid.isNotBlank()) {
            "${FormatUtils.urlEncode(node.username)}:${FormatUtils.urlEncode(node.passwordOrUuid)}@"
        } else ""
        val hostPort = FormatUtils.formatHostPort(node.server, node.port)
        return "$scheme://$auth$hostPort#${FormatUtils.urlEncode(node.name)}"
    }
    // endregion

    // region 辅助工具函数
    private fun safeParseUri(raw: String): URI? {
        return runCatching {
            URI(raw.replace(" ", "%20"))
        }.getOrNull()
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        rawQuery.split('&').forEach { pair ->
            if (pair.isBlank()) return@forEach
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = FormatUtils.urlDecode(pair.substring(0, idx))
                val value = FormatUtils.urlDecode(pair.substring(idx + 1))
                result[key] = value
            }
        }
        return result
    }

    private fun encodeQuery(params: Map<String, String>): String {
        return params.entries.joinToString("&") { (k, v) ->
            "${FormatUtils.urlEncode(k)}=${FormatUtils.urlEncode(v)}"
        }
    }

    private fun extractRemark(uri: URI, fallback: String): String {
        val frag = uri.rawFragment
        return if (!frag.isNullOrBlank()) FormatUtils.urlDecode(frag) else fallback
    }

    private fun splitHostPort(hostPort: String): Pair<String, Int>? {
        val trimmed = hostPort.trim().substringBefore("/")
        return if (trimmed.startsWith("[")) {
            val closeIdx = trimmed.indexOf(']')
            if (closeIdx <= 1) return null
            val host = trimmed.substring(1, closeIdx)
            val portStr = trimmed.substring(closeIdx + 1).removePrefix(":")
            val port = portStr.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            host to port
        } else {
            val colonIdx = trimmed.lastIndexOf(':')
            if (colonIdx <= 0) return null
            val host = trimmed.substring(0, colonIdx)
            val port = trimmed.substring(colonIdx + 1).toIntOrNull()?.takeIf { it in 1..65535 }
                ?: return null
            host to port
        }
    }

    private fun JsonObject.getStringOrEmpty(key: String): String {
        val elem = get(key) ?: return ""
        return if (elem.isJsonPrimitive) elem.asString else ""
    }

    private fun JsonObject.getIntOrDefault(key: String, default: Int): Int {
        val elem = get(key) ?: return default
        return runCatching { elem.asInt }.getOrDefault(default)
    }
    // endregion
}
