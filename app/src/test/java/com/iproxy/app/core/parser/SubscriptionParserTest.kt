package com.iproxy.app.core.parser

import com.iproxy.app.core.util.FormatUtils
import com.iproxy.app.data.model.ProtocolType
import com.iproxy.app.data.model.SecurityMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionParserTest {

    @Test
    fun `parses Base64 subscription with Subscription-Userinfo header`() {
        val rawLinks = listOf(
            "vless://uuid-1@us.example.com:443?security=tls&type=ws&path=/ws#Node1",
            "hy2://pass2@jp.example.com:8443?sni=jp.example.com#Node2"
        ).joinToString("\n")
        val encoded = FormatUtils.encodeBase64(rawLinks)
        val header = "upload=1048576; download=2097152; total=10737418240; expire=1893456000"

        val result = SubscriptionParser.parseContent(
            rawBody = encoded,
            subscriptionId = "sub-1",
            userInfoHeader = header
        )

        assertEquals(2, result.nodes.size)
        assertEquals("sub-1", result.nodes[0].subscriptionId)
        assertEquals(ProtocolType.VLESS, result.nodes[0].protocol)
        assertEquals(ProtocolType.HYSTERIA2, result.nodes[1].protocol)
        assertEquals(1048576L, result.uploadBytes)
        assertEquals(2097152L, result.downloadBytes)
        assertEquals(10737418240L, result.totalBytes)
        assertEquals(1893456000L, result.expireTimestampSec)
    }

    @Test
    fun `parses Clash YAML proxies block including inline and nested items`() {
        val yaml = """
            port: 7890
            proxies:
              - { name: "HK SS", type: ss, server: hk.example.com, port: 8388, cipher: aes-128-gcm, password: "secret" }
              - name: "US Reality"
                type: vless
                server: us.example.com
                port: 443
                uuid: 11111111-2222-3333-4444-555555555555
                tls: true
                servername: www.microsoft.com
                flow: xtls-rprx-vision
                reality-opts:
                  public-key: pbk123
                  short-id: sid456
              - name: "SG Hy2"
                type: hysteria2
                server: sg.example.com
                port: 443
                password: hy2password
                sni: sg.example.com
                obfs: salamander
                obfs-password: obfsp
            proxy-groups:
              - name: Proxy
        """.trimIndent()

        val result = SubscriptionParser.parseContent(yaml, subscriptionId = "sub-clash")
        assertEquals(3, result.nodes.size)
        assertEquals(ProtocolType.SHADOWSOCKS, result.nodes[0].protocol)
        assertEquals("HK SS", result.nodes[0].name)

        assertEquals(ProtocolType.VLESS, result.nodes[1].protocol)
        assertEquals(SecurityMode.REALITY, result.nodes[1].security)
        assertEquals("pbk123", result.nodes[1].realityPublicKey)
        assertEquals("sid456", result.nodes[1].realityShortId)

        assertEquals(ProtocolType.HYSTERIA2, result.nodes[2].protocol)
        assertEquals("salamander", result.nodes[2].hy2ObfsType)
        assertEquals("obfsp", result.nodes[2].hy2ObfsPassword)
        assertTrue(result.nodes.all { it.subscriptionId == "sub-clash" })
    }
}
