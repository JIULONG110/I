package com.iproxy.app.core.update

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.iproxy.app.BuildConfig
import com.iproxy.app.core.util.CoreLogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long
)

data class AppUpdateInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val releasePageUrl: String,
    val apkAsset: ReleaseAsset?
)

sealed class InstallLaunchResult {
    data object Launched : InstallLaunchResult()
    data object NeedsInstallPermission : InstallLaunchResult()
    data class SignatureMismatch(
        val installedCertShort: String,
        val archiveCertShort: String
    ) : InstallLaunchResult()

    data class Failed(val reason: String) : InstallLaunchResult()
}

internal data class ApkCompatibilityInput(
    val targetPackageName: String,
    val currentPackageName: String,
    val targetVersionCode: Long,
    val currentVersionCode: Long,
    val targetSignersSha256: Set<String>,
    val currentSignersSha256: Set<String>
)

internal sealed class ApkCompatibilityResult {
    data object Compatible : ApkCompatibilityResult()
    data class SignatureMismatch(
        val installedCertShort: String,
        val archiveCertShort: String
    ) : ApkCompatibilityResult()

    data class Invalid(val reason: String) : ApkCompatibilityResult()
}

/**
 * 应用自更新检查器与 APK 下载安装管理器
 *
 * 从 GitHub Releases 检查最新版本，按设备 CPU 架构自动匹配已签名的最优 APK 资产，
 * 并在调起系统安装器前执行完整性、版本号与签名证书一致性预检，避免隐晦的「未安装应用」报错。
 */
class AppUpdateManager(private val context: Context) {

    private val checkClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun checkForUpdate(
        currentVersion: String = BuildConfig.VERSION_NAME,
        supportedAbis: Array<String> = Build.SUPPORTED_ABIS
    ): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val releaseObj = fetchLatestReleaseJson() ?: return@runCatching null
            parseUpdateIfNewer(releaseObj, currentVersion, supportedAbis)
        }.onFailure { err ->
            CoreLogBuffer.w(TAG, "检查软件更新失败: ${err.message}")
        }
    }

    suspend fun downloadApk(
        asset: ReleaseAsset,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            // 使用 filesDir/updates 持久化下载中的安装包，避免授权「未知来源应用」期间被系统清理缓存目录
            val updatesDir = File(context.filesDir, UPDATES_DIR_NAME).apply { mkdirs() }
            cleanupOldUpdateFiles(updatesDir)

            if (asset.sizeBytes > 0L) {
                val minRequiredBytes = asset.sizeBytes + 20_000_000L
                if (updatesDir.usableSpace < minRequiredBytes) {
                    val needMb = minRequiredBytes / (1024 * 1024)
                    error("设备可用存储空间不足（至少需要 ${needMb} MB），清理空间后再试")
                }
            }

            val safeName = asset.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val partFile = File(updatesDir, "$safeName.part")
            val targetFile = File(updatesDir, safeName)

            val request = Request.Builder()
                .url(asset.downloadUrl)
                .header("User-Agent", "I-Android/${BuildConfig.VERSION_NAME}")
                .build()

            downloadClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("下载失败: HTTP ${resp.code}")
                }
                val body = resp.body
                val total = body.contentLength().takeIf { it > 0 } ?: asset.sizeBytes
                var downloaded = 0L
                val buffer = ByteArray(16 * 1024)

                body.byteStream().use { input ->
                    partFile.outputStream().use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                    }
                }
            }

            if (partFile.length() < 100_000L) {
                partFile.delete()
                error("下载的 APK 文件体积异常 (${partFile.length()} bytes)")
            }
            if (targetFile.exists()) targetFile.delete()
            if (!partFile.renameTo(targetFile)) {
                partFile.copyTo(targetFile, overwrite = true)
                partFile.delete()
            }

            // 预解析 APK 包结构，确认下载未损坏截断
            val archiveInfo = readPackageArchiveInfo(targetFile)
            if (archiveInfo == null) {
                targetFile.delete()
                error("下载的 APK 文件解析失败或未下载完整，请重试")
            }

            targetFile
        }
    }

    fun launchApkInstaller(apkFile: File): InstallLaunchResult {
        if (!apkFile.exists()) {
            return InstallLaunchResult.Failed("安装包文件不存在")
        }

        when (val compat = verifyApkForInstall(apkFile)) {
            is ApkCompatibilityResult.Invalid -> {
                return InstallLaunchResult.Failed(compat.reason)
            }
            is ApkCompatibilityResult.SignatureMismatch -> {
                CoreLogBuffer.w(
                    TAG,
                    "安装包签名不一致: installed=${compat.installedCertShort}, archive=${compat.archiveCertShort}"
                )
                return InstallLaunchResult.SignatureMismatch(
                    installedCertShort = compat.installedCertShort,
                    archiveCertShort = compat.archiveCertShort
                )
            }
            ApkCompatibilityResult.Compatible -> Unit
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return InstallLaunchResult.NeedsInstallPermission
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            InstallLaunchResult.Launched
        }.getOrElse { err ->
            InstallLaunchResult.Failed(err.message ?: "无法调起系统安装器")
        }
    }

    /**
     * 当无法直接覆盖安装（例如首次从旧临时测试签名迁移到统一正式签名）时，
     * 将已校验的 APK 导出到公共「下载/I/」目录，避免卸载旧版本时应用私有目录中的安装包被同时清除。
     */
    suspend fun saveApkToPublicDownloads(apkFile: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!apkFile.exists()) {
                error("安装包文件不存在，请重新下载")
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                error("当前系统版本低于 Android 10，请点击「浏览器下载」获取安装包")
            }

            val resolver = context.contentResolver
            val relPath = "${Environment.DIRECTORY_DOWNLOADS}/I"
            val exportName = if (apkFile.name.startsWith("I-", ignoreCase = true)) {
                apkFile.name
            } else {
                "I-${apkFile.name}"
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, exportName)
                put(MediaStore.Downloads.MIME_TYPE, APK_MIME_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, relPath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法写入系统「下载」目录")

            try {
                resolver.openOutputStream(uri)?.use { output ->
                    apkFile.inputStream().use { input ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                } ?: error("无法打开下载输出流")

                val doneValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(uri, doneValues, null, null)
            } catch (err: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw err
            }

            "$relPath/$exportName"
        }
    }

    fun launchUninstallCurrentApp(): Result<Unit> = runCatching {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun verifyApkForInstall(apkFile: File): ApkCompatibilityResult {
        val archiveInfo = readPackageArchiveInfo(apkFile)
            ?: return ApkCompatibilityResult.Invalid("安装包解析失败，文件可能已损坏或不完整")
        val installedInfo = readInstalledPackageInfo()
            ?: return ApkCompatibilityResult.Compatible

        val archivePackage = archiveInfo.packageName.orEmpty()
        val installedPackage = installedInfo.packageName ?: context.packageName
        val archiveVersionCode = readLongVersionCode(archiveInfo)
        val installedVersionCode = readLongVersionCode(installedInfo)
        val archiveSigners = extractSignerSha256Set(archiveInfo)
        val installedSigners = extractSignerSha256Set(installedInfo)

        return evaluateInstallCompatibility(
            ApkCompatibilityInput(
                targetPackageName = archivePackage,
                currentPackageName = installedPackage,
                targetVersionCode = archiveVersionCode,
                currentVersionCode = installedVersionCode,
                targetSignersSha256 = archiveSigners,
                currentSignersSha256 = installedSigners
            )
        )
    }

    private fun readPackageArchiveInfo(apkFile: File): PackageInfo? {
        val pm = context.packageManager
        val path = apkFile.absolutePath
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = getPackageArchiveInfoCompat(pm, path, PackageManager.GET_SIGNING_CERTIFICATES)
            if (info != null && extractSignerSha256Set(info).isNotEmpty()) {
                return info
            }
            @Suppress("DEPRECATION")
            return getPackageArchiveInfoCompat(pm, path, PackageManager.GET_SIGNATURES) ?: info
        }
        @Suppress("DEPRECATION")
        return getPackageArchiveInfoCompat(pm, path, PackageManager.GET_SIGNATURES)
    }

    private fun readInstalledPackageInfo(): PackageInfo? {
        val pm = context.packageManager
        val pkg = context.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = runCatching {
                getPackageInfoCompat(pm, pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            }.getOrNull()
            if (info != null && extractSignerSha256Set(info).isNotEmpty()) {
                return info
            }
            @Suppress("DEPRECATION")
            return runCatching {
                getPackageInfoCompat(pm, pkg, PackageManager.GET_SIGNATURES)
            }.getOrNull() ?: info
        }
        @Suppress("DEPRECATION")
        return runCatching {
            getPackageInfoCompat(pm, pkg, PackageManager.GET_SIGNATURES)
        }.getOrNull()
    }

    private fun getPackageArchiveInfoCompat(
        pm: PackageManager,
        archivePath: String,
        flags: Int
    ): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(archivePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(archivePath, flags)
        }
    }

    private fun getPackageInfoCompat(
        pm: PackageManager,
        packageName: String,
        flags: Int
    ): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, flags)
        }
    }

    private fun readLongVersionCode(info: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun extractSignerSha256Set(info: PackageInfo): Set<String> {
        val result = linkedSetOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { signingInfo ->
                signingInfo.apkContentsSigners?.forEach { sig ->
                    result += sha256Hex(sig.toByteArray())
                }
                signingInfo.signingCertificateHistory?.forEach { sig ->
                    result += sha256Hex(sig.toByteArray())
                }
            }
        }
        if (result.isEmpty()) {
            @Suppress("DEPRECATION")
            info.signatures?.forEach { sig ->
                result += sha256Hex(sig.toByteArray())
            }
        }
        return result
    }

    private fun cleanupOldUpdateFiles(updatesDir: File) {
        updatesDir.listFiles()?.forEach { old -> runCatching { old.delete() } }
        val legacyCacheDir = File(context.cacheDir, UPDATES_DIR_NAME)
        if (legacyCacheDir.exists()) {
            legacyCacheDir.listFiles()?.forEach { old -> runCatching { old.delete() } }
        }
    }

    private fun fetchLatestReleaseJson(): JsonObject? {
        val latestReq = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "I-Android/${BuildConfig.VERSION_NAME}")
            .build()

        checkClient.newCall(latestReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body.string()
                return JsonParser.parseString(body).asJsonObject
            }
            if (resp.code != 404) {
                error("GitHub Releases 查询失败: HTTP ${resp.code}")
            }
        }

        val listReq = Request.Builder()
            .url(RELEASES_LIST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "I-Android/${BuildConfig.VERSION_NAME}")
            .build()

        checkClient.newCall(listReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("GitHub Releases 列表查询失败: HTTP ${resp.code}")
            }
            val arr: JsonArray = JsonParser.parseString(resp.body.string()).asJsonArray
            for (el in arr) {
                val obj = el.asJsonObject
                val isDraft = obj.get("draft")?.asBoolean ?: false
                if (!isDraft) return obj
            }
        }
        return null
    }

    companion object {
        private const val TAG = "AppUpdateManager"
        private const val UPDATES_DIR_NAME = "updates"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val GITHUB_OWNER = "JIULONG110"
        private const val GITHUB_REPO = "I"
        const val RELEASES_PAGE_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        private const val RELEASES_LIST_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases?per_page=5"

        internal fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02X".format(it) }
        }

        internal fun evaluateInstallCompatibility(
            input: ApkCompatibilityInput
        ): ApkCompatibilityResult {
            if (input.targetPackageName != input.currentPackageName) {
                return ApkCompatibilityResult.Invalid(
                    "安装包应用标识不匹配 (${input.targetPackageName})"
                )
            }
            if (input.targetVersionCode < input.currentVersionCode) {
                return ApkCompatibilityResult.Invalid(
                    "安装包版本号 (${input.targetVersionCode}) 低于当前版本 (${input.currentVersionCode})，无法降级覆盖安装"
                )
            }
            if (input.targetSignersSha256.isEmpty()) {
                return ApkCompatibilityResult.Invalid("安装包缺少有效签名，无法安装")
            }
            if (input.currentSignersSha256.isNotEmpty() &&
                input.targetSignersSha256.intersect(input.currentSignersSha256).isEmpty()
            ) {
                return ApkCompatibilityResult.SignatureMismatch(
                    installedCertShort = input.currentSignersSha256.first().take(12),
                    archiveCertShort = input.targetSignersSha256.first().take(12)
                )
            }
            return ApkCompatibilityResult.Compatible
        }

        internal fun parseUpdateIfNewer(
            releaseObj: JsonObject,
            currentVersion: String,
            supportedAbis: Array<String>
        ): AppUpdateInfo? {
            if (releaseObj.get("draft")?.asBoolean == true) return null
            val tagName = releaseObj.get("tag_name")?.asString?.trim().orEmpty()
            if (tagName.isBlank()) return null

            if (!isRemoteVersionNewer(tagName, currentVersion)) {
                return null
            }

            val assets = mutableListOf<ReleaseAsset>()
            val assetsArr = releaseObj.getAsJsonArray("assets")
            if (assetsArr != null) {
                for (item in assetsArr) {
                    val obj = item.asJsonObject
                    val name = obj.get("name")?.asString.orEmpty()
                    val url = obj.get("browser_download_url")?.asString.orEmpty()
                    val size = obj.get("size")?.asLong ?: 0L
                    if (name.isNotBlank() && url.isNotBlank()) {
                        assets += ReleaseAsset(name = name, downloadUrl = url, sizeBytes = size)
                    }
                }
            }

            val bestAsset = selectBestApkAsset(assets, supportedAbis)
            val cleanVersion = tagName.removePrefix("v").removePrefix("V")
            val title = releaseObj.get("name")?.asString?.takeIf { it.isNotBlank() } ?: "发现新版本 $tagName"
            val notes = releaseObj.get("body")?.asString?.trim().orEmpty()
            val htmlUrl = releaseObj.get("html_url")?.asString?.takeIf { it.isNotBlank() } ?: RELEASES_PAGE_URL

            return AppUpdateInfo(
                tagName = tagName,
                versionName = cleanVersion,
                title = title,
                releaseNotes = notes,
                releasePageUrl = htmlUrl,
                apkAsset = bestAsset
            )
        }

        internal fun isRemoteVersionNewer(remoteVersion: String, currentVersion: String): Boolean {
            val remote = parseSemVer(remoteVersion) ?: return false
            val current = parseSemVer(currentVersion) ?: return false

            val maxLen = maxOf(remote.numbers.size, current.numbers.size)
            for (i in 0 until maxLen) {
                val r = remote.numbers.getOrElse(i) { 0 }
                val c = current.numbers.getOrElse(i) { 0 }
                if (r != c) return r > c
            }

            // 主版本号相同时，正式版高于预发布版
            if (remote.preRelease == null && current.preRelease != null) return true
            if (remote.preRelease != null && current.preRelease == null) return false
            if (remote.preRelease != null && current.preRelease != null) {
                return remote.preRelease > current.preRelease
            }
            return false
        }

        internal fun selectBestApkAsset(
            assets: List<ReleaseAsset>,
            supportedAbis: Array<String>
        ): ReleaseAsset? {
            val installableApks = assets.filter {
                it.name.endsWith(".apk", ignoreCase = true) &&
                    !it.name.contains("unsigned", ignoreCase = true)
            }
            if (installableApks.isEmpty()) return null

            // 同架构优先选择已签名的 Release 包，其次回退 Debug 包
            val orderedCandidates = installableApks.sortedBy { asset ->
                val lower = asset.name.lowercase()
                when {
                    "-release" in lower -> 0
                    "-debug" in lower -> 1
                    else -> 2
                }
            }

            for (abi in supportedAbis) {
                val match = orderedCandidates.firstOrNull { matchesAbiToken(it.name, abi) }
                if (match != null) return match
            }

            return orderedCandidates.firstOrNull {
                matchesAbiToken(it.name, "universal")
            } ?: orderedCandidates.firstOrNull()
        }

        internal fun matchesAbiToken(fileName: String, abi: String): Boolean {
            val normalizedAbi = abi.trim().lowercase()
            if (normalizedAbi.isEmpty()) return false
            val pattern = Regex("(^|[-.])${Regex.escape(normalizedAbi)}([-.]|$)")
            return pattern.containsMatchIn(fileName.lowercase())
        }

        private data class SemVer(
            val numbers: List<Int>,
            val preRelease: String?
        )

        private fun parseSemVer(raw: String): SemVer? {
            val trimmed = raw.trim().removePrefix("v").removePrefix("V")
            if (trimmed.isEmpty()) return null
            val withoutMeta = trimmed.substringBefore('+')
            val dashIdx = withoutMeta.indexOf('-')
            val corePart = if (dashIdx >= 0) withoutMeta.substring(0, dashIdx) else withoutMeta
            val prePart = if (dashIdx >= 0) withoutMeta.substring(dashIdx + 1).ifBlank { null } else null

            val numbers = corePart.split('.').map { seg ->
                seg.toIntOrNull() ?: return null
            }
            return SemVer(numbers = numbers, preRelease = prePart)
        }
    }
}
