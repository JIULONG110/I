package com.iproxy.app.service

import com.iproxy.app.data.model.AppSettings
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IVpnServiceConfigTest {

    @Test
    fun `hev tunnel yaml omits username and password so Xray noauth matches`() {
        val yaml = IVpnService.buildHevTunnelConfigYaml(AppSettings())

        assertFalse(yaml.contains("username"))
        assertFalse(yaml.contains("password"))
        assertFalse(yaml.contains("ipv6:"))
        assertTrue(yaml.contains("mtu: 1500"))
        assertTrue(yaml.contains("ipv4: 10.0.0.2"))
        assertTrue(yaml.contains("port: 10808"))
        assertTrue(yaml.contains("address: 127.0.0.1"))
        assertTrue(yaml.contains("udp: 'udp'"))
        assertTrue(yaml.contains("task-stack-size: 86016"))
    }

    @Test
    fun `hev tunnel yaml includes ipv6 and custom port when enabled`() {
        val yaml = IVpnService.buildHevTunnelConfigYaml(
            AppSettings(
                enableIpv6 = true,
                socksPort = 20808,
                vpnMtu = 1420
            )
        )

        assertTrue(yaml.contains("ipv6: 'fd00::2'"))
        assertTrue(yaml.contains("port: 20808"))
        assertTrue(yaml.contains("mtu: 1420"))
        assertFalse(yaml.contains("username"))
        assertFalse(yaml.contains("password"))
    }

    @Test
    fun `notification formatter exposes real node name, speeds, mode and disconnect label`() {
        val connectedVpn = VpnNotificationFormatter.formatConnected(
            nodeName = "美国静态 (链式代理)",
            protocolBadge = "链式代理·2跳",
            uplinkBps = 40L,
            downlinkBps = 2048L,
            proxyOnlyMode = false
        )
        assertTrue(connectedVpn.expandedTitle.contains("美国静态 (链式代理)"))
        assertTrue(connectedVpn.speedLine.contains("↑ 40 B/s"))
        assertTrue(connectedVpn.speedLine.contains("↓ 2.00 KB/s"))
        assertTrue(connectedVpn.speedLine.contains("VPN"))
        assertTrue(connectedVpn.statusBadge.contains("VPN"))
        assertTrue(connectedVpn.actionLabel == "断开连接")

        val connectedProxyOnly = VpnNotificationFormatter.formatConnected(
            nodeName = "香港高速",
            protocolBadge = "VLESS·REALITY",
            uplinkBps = 0L,
            downlinkBps = 0L,
            proxyOnlyMode = true
        )
        assertTrue(connectedProxyOnly.statusBadge.contains("纯代理"))
        assertTrue(connectedProxyOnly.speedLine.contains("纯代理"))

        val connecting = VpnNotificationFormatter.formatConnecting(nodeName = "日本节点", proxyOnlyMode = false)
        assertTrue(connecting.expandedTitle.contains("日本节点"))
        assertTrue(connecting.statusBadge.contains("连接中"))
    }
}
