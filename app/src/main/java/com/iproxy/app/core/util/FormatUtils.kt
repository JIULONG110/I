package com.iproxy.app.core.util

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

/**
 * 通用格式化与编解码工具
 */
object FormatUtils {

    /**
     * 格式化字节数为人类可读字符串（B / KB / MB / GB / TB）
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return if (index == 0) {
            "${bytes} B"
        } else {
            String.format(Locale.US, "%.2f %s", value, units[index])
        }
    }

    /**
     * 格式化实时网速（B/s / KB/s / MB/s）
     */
    fun formatSpeed(bytesPerSec: Long): String = "${formatBytes(bytesPerSec.coerceAtLeast(0L))}/s"

    /**
     * 格式化连接时长（HH:MM:SS）
     */
    fun formatDuration(durationMs: Long): String {
        val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSec / 3600L
        val minutes = (totalSec % 3600L) / 60L
        val seconds = totalSec % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * 安全 Base64 解码：自动兼容标准、URL-Safe、缺失填充 '=' 与空白字符
     */
    fun decodeBase64Safe(raw: String): String? {
        val cleaned = raw.trim()
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
        if (cleaned.isEmpty()) return null
        val normalized = cleaned
            .replace('-', '+')
            .replace('_', '/')
            .let { str ->
                val rem = str.length % 4
                if (rem == 0) str else str + "=".repeat(4 - rem)
            }
        return runCatching {
            String(Base64.getDecoder().decode(normalized), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    /**
     * 标准 Base64 编码（无换行）
     */
    fun encodeBase64(raw: String): String {
        return Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * URL-Safe Base64 编码（无填充）
     */
    fun encodeBase64UrlNoPad(raw: String): String {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    fun urlDecode(raw: String): String {
        return runCatching {
            URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
        }.getOrDefault(raw)
    }

    fun urlEncode(raw: String): String {
        return runCatching {
            URLEncoder.encode(raw, StandardCharsets.UTF_8.name()).replace("+", "%20")
        }.getOrDefault(raw)
    }

    /**
     * 判断是否为纯 IPv4 或 IPv6 字面量
     */
    fun isIpLiteral(host: String): Boolean {
        val trimmed = host.trim().removePrefix("[").removeSuffix("]")
        val ipv4 = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
        if (ipv4.matches(trimmed)) {
            return trimmed.split('.').all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
        }
        return trimmed.contains(':') && trimmed.all {
            it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.'
        }
    }

    /**
     * 安全格式化主机与端口（IPv6 自动加上方括号）
     */
    fun formatHostPort(host: String, port: Int): String {
        val clean = host.trim().removePrefix("[").removeSuffix("]")
        return if (clean.contains(':')) "[$clean]:$port" else "$clean:$port"
    }
}
