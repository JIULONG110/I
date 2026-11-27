package com.iproxy.app.core.xray

import com.google.gson.JsonParser
import com.iproxy.app.data.model.AppSettings
import com.iproxy.app.data.model.ProtocolType
import com.iproxy.app.data.model.RoutingMode
import com.iproxy.app.data.model.SecurityMode
import com.iproxy.app.data.model.ServerNode
import com.iproxy.app.data.model.TransportNetwork
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XrayConfigGeneratorTest {

    @Test
    fun `generates VLESS REALITY config with LAN share and CN bypass rules`() {
        val node = ServerNode(
            name = "Reality Node",
            protocol = ProtocolType.VLESS,
            server = "1.2.3.4",
            port = 443,
            passwordOrUuid = "uuid-abc",
            flow = "xtls-rprx-vision",
            network = TransportNetwork.TCP,
            security = SecurityMode.REALITY,
            sni = "www.apple.com",
            realityPublicKey = "pbkValue",
            realityShortId = "sidValue"
        )
        val settings = AppSettings(
            routingMode = RoutingMode.RULE_BYPASS_CN,
            blockAds = true,
            allowLanShare = true,
            enableMux = true,
            socksPort = 10808,
            httpPort = 10809
        )

        val json = XrayConfigGenerator.generateConfig(node, settings)
        val root = JsonParser.parseString(json).asJsonObject

        val inbounds = root.getAsJsonArray("inbounds")
        assertEquals(2, inbounds.size())
        assertEquals("0.0.0.0", inbounds[0].asJsonObject.get("listen").asString)
        assertEquals(10808, inbounds[0].asJsonObject.get("port").asInt)

        val proxyOutbound = root.getAsJsonArray("outbounds")[0].asJsonObject
        assertEquals("vless", proxyOutbound.get("protocol").asString)
        // XTLS Vision 禁用 mux
        assertFalse(proxyOutbound.getAsJsonObject("mux").get("enabled").asBoolean)
        val reality = proxyOutbound.getAsJsonObject("streamSettings").getAsJsonObject("realitySettings")
        assertEquals("pbkValue", reality.get("publicKey").asString)
        assertEquals("sidValue", reality.get("shortId").asString)

        val rulesStr = root.getAsJsonObject("routing").getAsJsonArray("rules").toString()
        assertTrue(rulesStr.contains("dns-out"))
        assertTrue(rulesStr.contains("dns-module"))
        assertTrue(rulesStr.contains("geosite:category-ads-all"))
        assertTrue(rulesStr.contains("geoip:cn"))
        assertTrue(rulesStr.contains("geosite:cn"))

        val outbounds = root.getAsJsonArray("outbounds")
        assertTrue(outbounds.any { it.asJsonObject.get("tag").asString == "dns-out" })
    }

    @Test
    fun `generates Hysteria2 config using protocol hysteria and finalmask`() {
        val node = ServerNode(
            name = "Hy2 Node",
            protocol = ProtocolType.HYSTERIA2,
            server = "hy.example.com",
            port = 443,
            passwordOrUuid = "secretAuth",
            security = SecurityMode.TLS,
            sni = "hy.example.com",
            hy2ObfsType = "salamander",
            hy2ObfsPassword = "obfsPassword",
            hy2PortHopping = "20000-21000"
        )
        val settings = AppSettings(routingMode = RoutingMode.GLOBAL_PROXY, blockAds = false)

        val json = XrayConfigGenerator.generateConfig(node, settings)
        val root = JsonParser.parseString(json).asJsonObject
        val outbound = root.getAsJsonArray("outbounds")[0].asJsonObject

        assertEquals("hysteria", outbound.get("protocol").asString)
        assertEquals(2, outbound.getAsJsonObject("settings").get("version").asInt)
        val stream = outbound.getAsJsonObject("streamSettings")
        assertEquals("hysteria", stream.get("network").asString)
        assertEquals("secretAuth", stream.getAsJsonObject("hysteriaSettings").get("auth").asString)

        val udpMasks = stream.getAsJsonObject("finalmask").getAsJsonArray("udp")
        assertEquals(2, udpMasks.size())
        assertEquals("salamander", udpMasks[0].asJsonObject.get("type").asString)
        assertEquals("udphop", udpMasks[1].asJsonObject.get("type").asString)

        val rulesStr = root.getAsJsonObject("routing").getAsJsonArray("rules").toString()
        assertFalse(rulesStr.contains("geoip:cn"))
        assertFalse(rulesStr.contains("geosite:category-ads-all"))
    }

    @Test
    fun `generates minimal speedtest config without inbounds`() {
        val node = ServerNode(
            name = "SS Node",
            protocol = ProtocolType.SHADOWSOCKS,
            server = "ss.example.com",
            port = 8388,
            passwordOrUuid = "pw",
            method = "aes-256-gcm"
        )
        val cfg = XrayConfigGenerator.generateSpeedTestConfig(node, AppSettings())
        val root = JsonParser.parseString(cfg).asJsonObject
        assertFalse(root.has("inbounds"))
        assertEquals(2, root.getAsJsonArray("outbounds").size())
    }

    @Test
    fun `pre-resolved proxy domain is written into dns hosts and sockopt`() {
        val node = ServerNode(
            name = "Domain Node",
            protocol = ProtocolType.VLESS,
            server = "proxy.example.com",
            port = 443,
            passwordOrUuid = "uuid-xyz",
            network = TransportNetwork.TCP,
            security = SecurityMode.REALITY,
            realityPublicKey = "pbk123"
        )
        val cfg = XrayConfigGenerator.generateConfig(
            node = node,
            settings = AppSettings(),
            resolvedHosts = mapOf("proxy.example.com" to listOf("203.0.113.10"))
        )
        val root = JsonParser.parseString(cfg).asJsonObject
        val hosts = root.getAsJsonObject("dns").getAsJsonObject("hosts")
        assertEquals("203.0.113.10", hosts.get("proxy.example.com").asString)

        val proxySockopt = root.getAsJsonArray("outbounds")[0]
            .asJsonObject
            .getAsJsonObject("streamSettings")
            .getAsJsonObject("sockopt")
        assertEquals("UseIPv4", proxySockopt.get("domainStrategy").asString)
    }
}
