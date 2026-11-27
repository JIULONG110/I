package com.iproxy.app.core.parser

import com.iproxy.app.core.util.FormatUtils
import com.iproxy.app.data.model.ProtocolType
import com.iproxy.app.data.model.SecurityMode
import com.iproxy.app.data.model.ServerNode
import com.iproxy.app.data.model.TransportNetwork

/**
 * 订阅内容与响应头解析结果
 */
data class SubscriptionParseResult(
    val nodes: List<ServerNode>,
    val uploadBytes: Long = -1L,
    val downloadBytes: Long = -1L,
    val totalBytes: Long = -1L,
    val expireTimestampSec: Long = -1L
)

/**
 * 多格式订阅与批量剪贴板内容解析引擎
 *
 * 自动兼容：
 * 1. Base64 编码的分享链接集合（最常见 V2Ray / Xray 机场订阅）
 * 2. 明文多行分享链接（vless://, vmess://, trojan://, ss://, hysteria2://, hy2://, wg://, socks://）
 * 3. Clash / Mihomo YAML `proxies:` 节点提取（让零基础用户可直接导入 Clash 订阅链接）
 * 4. 完整 Xray JSON 文本
 */
object SubscriptionParser {

    /**
     * 解析订阅响应体或剪贴板任意批量文本
     */
    fun parseContent(
        rawBody: String,
        subscriptionId: String? = null,
        userInfoHeader: String? = null
    ): SubscriptionParseResult {
        val headerStats = parseUserInfoHeader(userInfoHeader)
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) {
            return SubscriptionParseResult(
                nodes = emptyList(),
                uploadBytes = headerStats.upload,
                downloadBytes = headerStats.download,
                totalBytes = headerStats.total,
                expireTimestampSec = headerStats.expire
            )
        }

        val parsedNodes = mutableListOf<ServerNode>()

        // 1. 尝试直接作为 JSON 节点解析
        if (trimmed.startsWith("{")) {
            ShareLinkParser.parse(trimmed, subscriptionId)?.let { parsedNodes.add(it) }
        }

        // 2. 尝试识别 Clash YAML 格式（包含 proxies:）
        if (parsedNodes.isEmpty() && containsClashProxies(trimmed)) {
            parsedNodes.addAll(parseClashYamlProxies(trimmed, subscriptionId))
        }

        // 3. 尝试逐行解析明文链接
        if (parsedNodes.isEmpty()) {
            val directLines = parseLines(trimmed, subscriptionId)
            if (directLines.isNotEmpty()) {
                parsedNodes.addAll(directLines)
            }
        }

        // 4. 若明文无匹配，尝试整段 Base64 解码后再逐行解析
        if (parsedNodes.isEmpty()) {
            val decoded = FormatUtils.decodeBase64Safe(trimmed)
            if (!decoded.isNullOrBlank()) {
                if (containsClashProxies(decoded)) {
                    parsedNodes.addAll(parseClashYamlProxies(decoded, subscriptionId))
                } else {
                    parsedNodes.addAll(parseLines(decoded, subscriptionId))
                }
            }
        }

        return SubscriptionParseResult(
            nodes = parsedNodes,
            uploadBytes = headerStats.upload,
            downloadBytes = headerStats.download,
            totalBytes = headerStats.total,
            expireTimestampSec = headerStats.expire
        )
    }

    /**
     * 解析标准 Subscription-Userinfo 响应头
     * 示例: "upload=12345; download=67890; total=107374182400; expire=1800000000"
     */
    fun parseUserInfoHeader(header: String?): UserInfoStats {
        if (header.isNullOrBlank()) return UserInfoStats()
        var upload = -1L
        var download = -1L
        var total = -1L
        var expire = -1L
        header.split(';').forEach { part ->
            val kv = part.trim().split('=', limit = 2)
            if (kv.size == 2) {
                val value = kv[1].trim().toLongOrNull() ?: return@forEach
                when (kv[0].trim().lowercase()) {
                    "upload" -> upload = value
                    "download" -> download = value
                    "total" -> total = value
                    "expire" -> expire = value
                }
            }
        }
        return UserInfoStats(upload, download, total, expire)
    }

    data class UserInfoStats(
        val upload: Long = -1L,
        val download: Long = -1L,
        val total: Long = -1L,
        val expire: Long = -1L
    )

    private fun parseLines(text: String, subscriptionId: String?): List<ServerNode> {
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
            .mapNotNull { line -> ShareLinkParser.parse(line, subscriptionId) }
    }

    private fun containsClashProxies(text: String): Boolean {
        return text.lines().any { it.trimStart().startsWith("proxies:") }
    }

    /**
     * 轻量级 Clash / Mihomo YAML `proxies:` 段解析器（兼容块状 YAML 与单行 `{...}` 流式对象）
     */
    private fun parseClashYamlProxies(yaml: String, subscriptionId: String?): List<ServerNode> {
        val result = mutableListOf<ServerNode>()
        val lines = yaml.lines()
        var inProxies = false
        var baseIndent = -1
        val itemMaps = mutableListOf<MutableMap<String, String>>()
        var currentMap: MutableMap<String, String>? = null
        var nestedPrefix = ""

        for (rawLine in lines) {
            val lineWithoutComment = stripYamlComment(rawLine)
            if (lineWithoutComment.isBlank()) continue
            val indent = rawLine.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val trimmed = lineWithoutComment.trim()

            if (!inProxies) {
                if (trimmed == "proxies:" || trimmed.startsWith("proxies:")) {
                    inProxies = true
                    baseIndent = indent
                }
                continue
            }

            // 遇到同级或更外层顶级键（例如 proxy-groups: / rules:），结束 proxies 段
            if (indent <= baseIndent && !trimmed.startsWith("-")) {
                break
            }

            if (trimmed.startsWith("- ")) {
                val afterDash = trimmed.removePrefix("-").trim()
                if (afterDash.startsWith("{") && afterDash.endsWith("}")) {
                    // 单行流式格式: - { name: ..., type: ss, server: ..., port: 443, ... }
                    val inlineMap = parseInlineYamlMap(afterDash)
                    if (inlineMap.isNotEmpty()) {
                        itemMaps.add(inlineMap)
                    }
                    currentMap = null
                    nestedPrefix = ""
                } else {
                    currentMap = linkedMapOf()
                    itemMaps.add(currentMap)
                    nestedPrefix = ""
                    parseYamlKeyValue(afterDash)?.let { (k, v) ->
                        currentMap[k] = v
                    }
                }
            } else if (currentMap != null) {
                if (trimmed.endsWith(":") && !trimmed.contains(": ")) {
                    nestedPrefix = trimmed.removeSuffix(":").trim() + "."
                } else {
                    if (indent <= baseIndent + 2) {
                        nestedPrefix = ""
                    }
                    parseYamlKeyValue(trimmed)?.let { (k, v) ->
                        currentMap["$nestedPrefix$k"] = v
                    }
                }
            }
        }

        for (map in itemMaps) {
            convertClashMapToNode(map, subscriptionId)?.let { result.add(it) }
        }
        return result
    }

    private fun parseInlineYamlMap(inline: String): MutableMap<String, String> {
        val inner = inline.removePrefix("{").removeSuffix("}").trim()
        val map = linkedMapOf<String, String>()
        // 简单拆分不在花括号/引号内的逗号
        var braceDepth = 0
        var inQuote: Char? = null
        val sb = StringBuilder()
        val parts = mutableListOf<String>()
        for (ch in inner) {
            when {
                inQuote != null -> {
                    sb.append(ch)
                    if (ch == inQuote) inQuote = null
                }
                ch == '\'' || ch == '"' -> {
                    inQuote = ch
                    sb.append(ch)
                }
                ch == '{' || ch == '[' -> {
                    braceDepth++
                    sb.append(ch)
                }
                ch == '}' || ch == ']' -> {
                    braceDepth--
                    sb.append(ch)
                }
                ch == ',' && braceDepth == 0 -> {
                    parts.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) parts.add(sb.toString())

        for (part in parts) {
            val trimmed = part.trim()
            val kv = parseYamlKeyValue(trimmed) ?: continue
            val key = kv.first
            val valStr = kv.second
            if (valStr.startsWith("{") && valStr.endsWith("}")) {
                val nested = parseInlineYamlMap(valStr)
                nested.forEach { (nk, nv) -> map["$key.$nk"] = nv }
            } else {
                map[key] = valStr
            }
        }
        return map
    }

    private fun parseYamlKeyValue(line: String): Pair<String, String>? {
        val idx = line.indexOf(':')
        if (idx <= 0) return null
        val key = line.substring(0, idx).trim().trim('"', '\'')
        val value = line.substring(idx + 1).trim().trim('"', '\'')
        return key to value
    }

    private fun stripYamlComment(line: String): String {
        var inSingle = false
        var inDouble = false
        for (i in line.indices) {
            when (line[i]) {
                '\'' -> if (!inDouble) inSingle = !inSingle
                '"' -> if (!inSingle) inDouble = !inDouble
                '#' -> if (!inSingle && !inDouble && (i == 0 || line[i - 1].isWhitespace())) {
                    return line.substring(0, i)
                }
            }
        }
        return line
    }

    private fun convertClashMapToNode(
        m: Map<String, String>,
        subscriptionId: String?
    ): ServerNode? {
        val type = m["type"]?.lowercase() ?: return null
        val name = m["name"].orEmpty().ifBlank { "${m["server"]}:${m["port"]}" }
        val server = m["server"]?.takeIf { it.isNotBlank() } ?: return null
        val port = m["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null

        val network = TransportNetwork.fromString(m["network"])
        val sni = m["sni"] ?: m["servername"].orEmpty()
        val tls = m["tls"] == "true"
        val realityPbk = m["reality-opts.public-key"].orEmpty()
        val realitySid = m["reality-opts.short-id"].orEmpty()
        val security = when {
            realityPbk.isNotBlank() -> SecurityMode.REALITY
            tls -> SecurityMode.TLS
            else -> SecurityMode.NONE
        }
        val insecure = m["skip-cert-verify"] == "true"
        val wsPath = m["ws-opts.path"].orEmpty()
        val wsHost = m["ws-opts.headers.Host"].orEmpty()
        val grpcService = m["grpc-opts.grpc-service-name"].orEmpty()

        return when (type) {
            "vless" -> ServerNode(
                name = name,
                protocol = ProtocolType.VLESS,
                server = server,
                port = port,
                subscriptionId = subscriptionId,
                passwordOrUuid = m["uuid"].orEmpty(),
                flow = m["flow"].orEmpty(),
                network = network,
                path = wsPath,
                host = wsHost,
                grpcServiceName = grpcService,
                security = security,
                sni = sni,
                fingerprint = m["client-fingerprint"].orEmpty(),
                allowInsecure = insecure,
                realityPublicKey = realityPbk,
                realityShortId = realitySid
            )
            "vmess" -> ServerNode(
                name = name,
                protocol = ProtocolType.VMESS,
                server = server,
                port = port,
                subscriptionId = subscriptionId,
                passwordOrUuid = m["uuid"].orEmpty(),
                alterId = m["alterId"]?.toIntOrNull() ?: 0,
                method = m["cipher"].orEmpty().ifBlank { "auto" },
                network = network,
                path = wsPath,
                host = wsHost,
                grpcServiceName = grpcService,
                security = security,
                sni = sni,
                fingerprint = m["client-fingerprint"].orEmpty(),
                allowInsecure = insecure
            )
            "trojan" -> ServerNode(
                name = name,
                protocol = ProtocolType.TROJAN,
                server = server,
                port = port,
                subscriptionId = subscriptionId,
                passwordOrUuid = m["password"].orEmpty(),
                network = network,
                path = wsPath,
                host = wsHost,
                grpcServiceName = grpcService,
                security = if (realityPbk.isNotBlank()) SecurityMode.REALITY else SecurityMode.TLS,
                sni = sni,
                fingerprint = m["client-fingerprint"].orEmpty(),
                allowInsecure = insecure,
                realityPublicKey = realityPbk,
                realityShortId = realitySid
            )
            "ss" -> ServerNode(
                name = name,
                protocol = ProtocolType.SHADOWSOCKS,
                server = server,
                port = port,
                subscriptionId = subscriptionId,
                passwordOrUuid = m["password"].orEmpty(),
                method = m["cipher"].orEmpty().ifBlank { "aes-128-gcm" }
            )
            "hysteria2" -> ServerNode(
                name = name,
                protocol = ProtocolType.HYSTERIA2,
                server = server,
                port = port,
                subscriptionId = subscriptionId,
                passwordOrUuid = m["password"].orEmpty(),
                network = TransportNetwork.HYSTERIA,
                security = SecurityMode.TLS,
                sni = sni,
                allowInsecure = insecure,
                hy2ObfsType = m["obfs"].orEmpty(),
                hy2ObfsPassword = m["obfs-password"].orEmpty(),
                hy2PortHopping = m["ports"].orEmpty()
            )
            "socks5" -> ServerNode(
                name = name,
                protocol = ProtocolType.SOCKS,
                server = server,
                port = port,
                subscriptionId = subscriptionId,
                username = m["username"].orEmpty(),
                passwordOrUuid = m["password"].orEmpty()
            )
            "http" -> ServerNode(
                name = name,
                protocol = ProtocolType.HTTP,
                server = server,
                port = port,
                subscriptionId = subscriptionId,
                username = m["username"].orEmpty(),
                passwordOrUuid = m["password"].orEmpty(),
                security = if (tls) SecurityMode.TLS else SecurityMode.NONE
            )
            else -> null
        }
    }
}
