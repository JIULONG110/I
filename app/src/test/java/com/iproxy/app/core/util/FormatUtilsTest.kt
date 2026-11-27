package com.iproxy.app.core.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FormatUtilsTest {

    @Test
    fun `formats bytes speed and duration properly`() {
        assertEquals("0 B", FormatUtils.formatBytes(0))
        assertEquals("512 B", FormatUtils.formatBytes(512))
        assertEquals("1.50 KB", FormatUtils.formatBytes(1536))
        assertEquals("2.00 MB/s", FormatUtils.formatSpeed(2 * 1024 * 1024))
        assertEquals("01:01:05", FormatUtils.formatDuration(3665_000L))
    }

    @Test
    fun `decodes url-safe unpadded base64 and validates IP literals`() {
        val raw = "hello-proxy-world!"
        val encoded = FormatUtils.encodeBase64UrlNoPad(raw)
        assertEquals(raw, FormatUtils.decodeBase64Safe(encoded))

        assertTrue(FormatUtils.isIpLiteral("1.1.1.1"))
        assertTrue(FormatUtils.isIpLiteral("2001:db8::1"))
        assertFalse(FormatUtils.isIpLiteral("999.1.1.1"))
        assertFalse(FormatUtils.isIpLiteral("example.com"))

        assertEquals("[2001:db8::1]:443", FormatUtils.formatHostPort("2001:db8::1", 443))
        assertEquals("example.com:443", FormatUtils.formatHostPort("example.com", 443))
    }
}
