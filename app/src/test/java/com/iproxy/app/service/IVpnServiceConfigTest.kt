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
}
