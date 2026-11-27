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
    fun `selectBestApkAsset prefers signed release and avoids x86 matching x86_64`() {
        val assets = listOf(
            ReleaseAsset("app-arm64-v8a-release-unsigned.apk", "https://example.com/unsigned.apk", 100),
            ReleaseAsset("app-arm64-v8a-debug.apk", "https://example.com/v8-debug.apk", 300),
            ReleaseAsset("app-arm64-v8a-release.apk", "https://example.com/v8-release.apk", 200),
            ReleaseAsset("app-x86_64-release.apk", "https://example.com/x64-release.apk", 210),
            ReleaseAsset("app-universal-release.apk", "https://example.com/uni-release.apk", 400)
        )

        val pickedArm64 = AppUpdateManager.selectBestApkAsset(assets, arrayOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("app-arm64-v8a-release.apk", pickedArm64?.name)

        // 32-bit x86 device must NOT match app-x86_64-release.apk
        val pickedX86 = AppUpdateManager.selectBestApkAsset(assets, arrayOf("x86"))
        assertEquals("app-universal-release.apk", pickedX86?.name)

        // Fallback to debug when release is still unsigned (backward compatibility with old releases)
        val debugOnlyAssets = listOf(
            ReleaseAsset("app-arm64-v8a-release-unsigned.apk", "https://example.com/unsigned.apk", 100),
            ReleaseAsset("app-arm64-v8a-debug.apk", "https://example.com/v8-debug.apk", 300)
        )
        assertEquals(
            "app-arm64-v8a-debug.apk",
            AppUpdateManager.selectBestApkAsset(debugOnlyAssets, arrayOf("arm64-v8a"))?.name
        )

        val onlyUnsigned = listOf(
            ReleaseAsset("app-arm64-v8a-release-unsigned.apk", "https://example.com/u.apk", 100)
        )
        assertNull(AppUpdateManager.selectBestApkAsset(onlyUnsigned, arrayOf("arm64-v8a")))
    }

    @Test
    fun `evaluateInstallCompatibility detects signature mismatch downgrade and package conflict`() {
        val certA = "AA11223344556677889900AABBCCDDEEFF"
        val certB = "FF00112233445566778899AABBCCDDEE11"

        val compatible = AppUpdateManager.evaluateInstallCompatibility(
            ApkCompatibilityInput(
                targetPackageName = "com.iproxy.app",
                currentPackageName = "com.iproxy.app",
                targetVersionCode = 104,
                currentVersionCode = 103,
                targetSignersSha256 = setOf(certA),
                currentSignersSha256 = setOf(certA)
            )
        )
        assertEquals(ApkCompatibilityResult.Compatible, compatible)

        val mismatch = AppUpdateManager.evaluateInstallCompatibility(
            ApkCompatibilityInput(
                targetPackageName = "com.iproxy.app",
                currentPackageName = "com.iproxy.app",
                targetVersionCode = 103,
                currentVersionCode = 101,
                targetSignersSha256 = setOf(certB),
                currentSignersSha256 = setOf(certA)
            )
        )
        assertTrue(mismatch is ApkCompatibilityResult.SignatureMismatch)

        val downgrade = AppUpdateManager.evaluateInstallCompatibility(
            ApkCompatibilityInput(
                targetPackageName = "com.iproxy.app",
                currentPackageName = "com.iproxy.app",
                targetVersionCode = 101,
                currentVersionCode = 103,
                targetSignersSha256 = setOf(certA),
                currentSignersSha256 = setOf(certA)
            )
        )
        assertTrue(downgrade is ApkCompatibilityResult.Invalid)

        val wrongPkg = AppUpdateManager.evaluateInstallCompatibility(
            ApkCompatibilityInput(
                targetPackageName = "com.other.app",
                currentPackageName = "com.iproxy.app",
                targetVersionCode = 104,
                currentVersionCode = 103,
                targetSignersSha256 = setOf(certA),
                currentSignersSha256 = setOf(certA)
            )
        )
        assertTrue(wrongPkg is ApkCompatibilityResult.Invalid)

        val unsigned = AppUpdateManager.evaluateInstallCompatibility(
            ApkCompatibilityInput(
                targetPackageName = "com.iproxy.app",
                currentPackageName = "com.iproxy.app",
                targetVersionCode = 104,
                currentVersionCode = 103,
                targetSignersSha256 = emptySet(),
                currentSignersSha256 = setOf(certA)
            )
        )
        assertTrue(unsigned is ApkCompatibilityResult.Invalid)
    }
}
