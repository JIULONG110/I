package com.iproxy.app.core.xray

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.iproxy.app.data.model.AppSettings
import com.iproxy.app.data.model.PolicyStrategy
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

    @Test
    fun `generates MASQUE outbound with masqueSettings and WARP config`() {
        val node = ServerNode(
            name = "CF MASQUE",
            protocol = ProtocolType.MASQUE,
            server = "consumer-masque.cloudflareclient.com",
            port = 443,
            sni = "consumer-masque.cloudflareclient.com",
            host = "cloudflareaccess.com",
            masquePath = "/",
            masqueRemoteDns = "1.1.1.1,8.8.8.8",
            masqueWarpPrivateKey = "privKeyPem",
            masqueWarpPublicKey = "pubKeyPem",
            masqueWarpAddress = "172.16.0.2/32"
        )
        val cfg = XrayConfigGenerator.generateConfig(node, AppSettings())
        val root = JsonParser.parseString(cfg).asJsonObject
        val outbound = root.getAsJsonArray("outbounds")[0].asJsonObject

        assertEquals("masque", outbound.get("protocol").asString)
        val settings = outbound.getAsJsonObject("settings")
        assertEquals("consumer-masque.cloudflareclient.com", settings.get("address").asString)
        assertEquals(2, settings.getAsJsonArray("remoteDNS").size())

        val stream = outbound.getAsJsonObject("streamSettings")
        assertEquals("masque", stream.get("network").asString)
        assertEquals("tls", stream.get("security").asString)
        val masqueSettings = stream.getAsJsonObject("masqueSettings")
        assertEquals("/", masqueSettings.get("path").asString)
        assertEquals("cloudflareaccess.com", masqueSettings.get("host").asString)
        val warp = masqueSettings.getAsJsonObject("warp")
        assertEquals("privKeyPem", warp.get("privateKey").asString)
        assertEquals("pubKeyPem", warp.get("publicKey").asString)
        assertEquals("172.16.0.2/32", warp.getAsJsonArray("address")[0].asString)
    }

    @Test
    fun `generates PolicyGroup balancer and observatory routing`() {
        val n1 = ServerNode(
            id = "n1",
            name = "HK 1",
            protocol = ProtocolType.VLESS,
            server = "hk1.example.com",
            port = 443,
            passwordOrUuid = "u1"
        )
        val n2 = ServerNode(
            id = "n2",
            name = "HK 2",
            protocol = ProtocolType.TROJAN,
            server = "hk2.example.com",
            port = 443,
            passwordOrUuid = "pw2"
        )
        val group = ServerNode(
            id = "g1",
            name = "Auto HK",
            protocol = ProtocolType.POLICY_GROUP,
            server = "group",
            port = 443,
            policyStrategy = PolicyStrategy.LEAST_PING,
            childNodeIds = listOf("n1", "n2")
        )

        val hosts = XrayConfigGenerator.collectTargetHosts(group, listOf(n1, n2, group))
        assertEquals(listOf("hk1.example.com", "hk2.example.com"), hosts)

        val cfg = XrayConfigGenerator.generateConfig(group, AppSettings(), emptyMap(), listOf(n1, n2, group))
        val root = JsonParser.parseString(cfg).asJsonObject

        assertTrue(root.has("observatory"))
        val balancers = root.getAsJsonObject("routing").getAsJsonArray("balancers")
        assertEquals(1, balancers.size())
        assertEquals("proxy-balancer", balancers[0].asJsonObject.get("tag").asString)
        assertEquals("leastPing", balancers[0].asJsonObject.getAsJsonObject("strategy").get("type").asString)

        val outTags = root.getAsJsonArray("outbounds").map { it.asJsonObject.get("tag").asString }
        assertTrue(outTags.contains("proxy-1"))
        assertTrue(outTags.contains("proxy-2"))

        val rulesStr = root.getAsJsonObject("routing").getAsJsonArray("rules").toString()
        assertTrue(rulesStr.contains("proxy-balancer"))
    }

    @Test
    fun `generates ProxyChain with reverse dialerProxy hops and CustomOutbound wrapping`() {
        val entry = ServerNode(
            id = "entry",
            name = "Entry Relay",
            protocol = ProtocolType.SOCKS,
            server = "relay.example.com",
            port = 1080
        )
        val customExit = ServerNode(
            id = "exitCustom",
            name = "Custom Exit",
            protocol = ProtocolType.CUSTOM_OUTBOUND,
            server = "outbound",
            port = 443,
            customRawJson = """{"protocol":"vless","settings":{"address":"exit.example.com","port":443,"id":"uuid1","encryption":"none"}}"""
        )
        val chain = ServerNode(
            id = "chain1",
            name = "Relay -> Exit",
            protocol = ProtocolType.PROXY_CHAIN,
            server = "chain",
            port = 443,
            childNodeIds = listOf("entry", "exitCustom")
        )

        val allNodes = listOf(entry, customExit, chain)
        val cfg = XrayConfigGenerator.generateConfig(
            node = chain,
            settings = AppSettings(),
            resolvedHosts = mapOf("relay.example.com" to listOf("198.51.100.5")),
            allNodes = allNodes
        )
        val root = JsonParser.parseString(cfg).asJsonObject
        val outbounds = root.getAsJsonArray("outbounds")

        val exitOutbound = outbounds[0].asJsonObject
        val entryOutbound = outbounds[1].asJsonObject
        assertEquals("proxy", exitOutbound.get("tag").asString)
        assertEquals(
            "chain-proxy-1",
            exitOutbound.getAsJsonObject("streamSettings").getAsJsonObject("sockopt").get("dialerProxy").asString
        )

        assertEquals("chain-proxy-1", entryOutbound.get("tag").asString)
        val entrySockopt = entryOutbound.getAsJsonObject("streamSettings").getAsJsonObject("sockopt")
        assertFalse(entrySockopt.has("dialerProxy"))
        assertEquals("UseIPv4", entrySockopt.get("domainStrategy").asString)

        val speedCfg = XrayConfigGenerator.generateSpeedTestConfig(chain, AppSettings(), allNodes)
        val speedRoot = JsonParser.parseString(speedCfg).asJsonObject
        assertEquals(3, speedRoot.getAsJsonArray("outbounds").size())
    }

    @Test
    fun `sanitized gracefully initializes legacy Gson payloads missing new fields`() {
        val legacyJson = """[{"id":"old-1","name":"Legacy","protocol":"VLESS","server":"1.1.1.1","port":443}]"""
        val type = object : TypeToken<List<ServerNode>>() {}.type
        val parsed = Gson().fromJson<List<ServerNode>>(legacyJson, type).map { it.sanitized() }
        val node = parsed.first()
        assertEquals("", node.masquePath)
        assertEquals(emptyList<String>(), node.childNodeIds)
        assertEquals(PolicyStrategy.LEAST_PING, node.policyStrategy)
        assertEquals("VLESS", node.badgeText())
    }
}
