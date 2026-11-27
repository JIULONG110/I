package com.iproxy.app.core.parser

import com.iproxy.app.core.util.FormatUtils
import com.iproxy.app.data.model.ProtocolType
import com.iproxy.app.data.model.SecurityMode
import com.iproxy.app.data.model.TransportNetwork
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShareLinkParserTest {

    @Test
    fun `parses and serializes VLESS REALITY link`() {
        val raw = "vless://11111111-2222-3333-4444-555555555555@1.2.3.4:443" +
            "?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.apple.com" +
            "&fp=chrome&pbk=pubKeyAbc123&sid=a1b2&spx=%2F&type=tcp#US%20Reality"

        val node = ShareLinkParser.parse(raw)
        assertNotNull(node)
        assertEquals(ProtocolType.VLESS, node!!.protocol)
        assertEquals("US Reality", node.name)
        assertEquals("1.2.3.4", node.server)
        assertEquals(443, node.port)
        assertEquals("11111111-2222-3333-4444-555555555555", node.passwordOrUuid)
        assertEquals("xtls-rprx-vision", node.flow)
        assertEquals(SecurityMode.REALITY, node.security)
        assertEquals("www.apple.com", node.sni)
        assertEquals("pubKeyAbc123", node.realityPublicKey)
        assertEquals("a1b2", node.realityShortId)

        val roundTrip = ShareLinkParser.parse(ShareLinkParser.toShareLink(node))
        assertNotNull(roundTrip)
        assertEquals(node.server, roundTrip!!.server)
        assertEquals(node.realityPublicKey, roundTrip.realityPublicKey)
        assertEquals(node.name, roundTrip.name)
    }

    @Test
    fun `parses VMess Base64 JSON link`() {
        val json = """
            {"v":"2","ps":"东京WS","add":"jp.example.com","port":"443","id":"abcd-1234","aid":"0","scy":"auto","net":"ws","type":"none","host":"cdn.example.com","path":"/ray","tls":"tls","sni":"jp.example.com"}
        """.trimIndent()
        val raw = "vmess://${FormatUtils.encodeBase64(json)}"

        val node = ShareLinkParser.parse(raw)
        assertNotNull(node)
        assertEquals(ProtocolType.VMESS, node!!.protocol)
        assertEquals("东京WS", node.name)
        assertEquals("jp.example.com", node.server)
        assertEquals(443, node.port)
        assertEquals(TransportNetwork.WS, node.network)
        assertEquals(SecurityMode.TLS, node.security)
        assertEquals("/ray", node.path)
        assertEquals("cdn.example.com", node.host)
    }

    @Test
    fun `parses Hysteria2 link with salamander obfs and port hopping`() {
        val raw = "hy2://mysecret@hy.example.com:8443?sni=bing.com&insecure=1" +
            "&obfs=salamander&obfs-password=obfspass&mport=20000-30000#HK-Hy2"

        val node = ShareLinkParser.parse(raw)
        assertNotNull(node)
        assertEquals(ProtocolType.HYSTERIA2, node!!.protocol)
        assertEquals("HK-Hy2", node.name)
        assertEquals("mysecret", node.passwordOrUuid)
        assertEquals("bing.com", node.sni)
        assertTrue(node.allowInsecure)
        assertEquals("salamander", node.hy2ObfsType)
        assertEquals("obfspass", node.hy2ObfsPassword)
        assertEquals("20000-30000", node.hy2PortHopping)

        val reparsed = ShareLinkParser.parse(ShareLinkParser.toShareLink(node))
        assertNotNull(reparsed)
        assertEquals("salamander", reparsed!!.hy2ObfsType)
        assertEquals("20000-30000", reparsed.hy2PortHopping)
    }

    @Test
    fun `parses Shadowsocks SIP002 and legacy links`() {
        val cred = FormatUtils.encodeBase64UrlNoPad("aes-256-gcm:pass123")
        val sip002 = "ss://$cred@ss.example.com:8388#SG-SS"
        val node = ShareLinkParser.parse(sip002)
        assertNotNull(node)
        assertEquals(ProtocolType.SHADOWSOCKS, node!!.protocol)
        assertEquals("aes-256-gcm", node.method)
        assertEquals("pass123", node.passwordOrUuid)
        assertEquals("SG-SS", node.name)

        val legacy = "ss://${FormatUtils.encodeBase64("chacha20-ietf-poly1305:secret@1.1.1.1:9000")}#Legacy"
        val legacyNode = ShareLinkParser.parse(legacy)
        assertNotNull(legacyNode)
        assertEquals("chacha20-ietf-poly1305", legacyNode!!.method)
        assertEquals("1.1.1.1", legacyNode.server)
        assertEquals(9000, legacyNode.port)
    }

    @Test
    fun `parses Trojan and WireGuard and rejects invalid input`() {
        val trojan = ShareLinkParser.parse("trojan://pw123@tr.example.com:443?sni=tr.example.com&type=grpc&serviceName=grpc#TR")
        assertNotNull(trojan)
        assertEquals(ProtocolType.TROJAN, trojan!!.protocol)
        assertEquals(TransportNetwork.GRPC, trojan.network)
        assertEquals("grpc", trojan.grpcServiceName)

        val wg = ShareLinkParser.parse("wireguard://privKey@198.51.100.2:51820?publickey=pubKey&address=10.0.0.5/32&reserved=1,2,3&mtu=1280#WG")
        assertNotNull(wg)
        assertEquals(ProtocolType.WIREGUARD, wg!!.protocol)
        assertEquals("privKey", wg.wgPrivateKey)
        assertEquals("pubKey", wg.wgPublicKey)
        assertEquals(1280, wg.wgMtu)

        assertNull(ShareLinkParser.parse("invalid://not-supported"))
        assertNull(ShareLinkParser.parse("   "))
    }
}
