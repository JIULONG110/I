package com.iproxy.app.core.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppUpdateManagerTest {

    @Test
    fun `isRemoteVersionNewer handles semver comparisons accurately`() {
        assertTrue(AppUpdateManager.isRemoteVersionNewer("v1.0.1", "1.0.0"))
        assertTrue(AppUpdateManager.isRemoteVersionNewer("1.1.0", "1.0.9"))
        assertTrue(AppUpdateManager.isRemoteVersionNewer("V2.0.0", "1.9.9"))
        assertTrue(AppUpdateManager.isRemoteVersionNewer("v1.0.1", "1.0.1-rc1"))

        assertFalse(AppUpdateManager.isRemoteVersionNewer("v1.0.0", "1.0.0"))
        assertFalse(AppUpdateManager.isRemoteVersionNewer("v0.9.9", "1.0.0"))
        assertFalse(AppUpdateManager.isRemoteVersionNewer("v1.0.0-beta", "1.0.0"))
    }

    @Test
    fun `selectBestApkAsset matches device abi and skips unsigned builds`() {
        val assets = listOf(
            ReleaseAsset("app-arm64-v8a-release-unsigned.apk", "https://example.com/unsigned.apk", 100),
            ReleaseAsset("app-armeabi-v7a-debug.apk", "https://example.com/v7.apk", 200),
            ReleaseAsset("app-arm64-v8a-debug.apk", "https://example.com/v8.apk", 300),
            ReleaseAsset("app-universal-debug.apk", "https://example.com/uni.apk", 400)
        )

        val pickedArm64 = AppUpdateManager.selectBestApkAsset(assets, arrayOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("app-arm64-v8a-debug.apk", pickedArm64?.name)

        val pickedFallbackUniversal = AppUpdateManager.selectBestApkAsset(assets, arrayOf("riscv64"))
        assertEquals("app-universal-debug.apk", pickedFallbackUniversal?.name)

        val onlyUnsigned = listOf(
            ReleaseAsset("app-arm64-v8a-release-unsigned.apk", "https://example.com/u.apk", 100)
        )
        assertNull(AppUpdateManager.selectBestApkAsset(onlyUnsigned, arrayOf("arm64-v8a")))
    }
}
