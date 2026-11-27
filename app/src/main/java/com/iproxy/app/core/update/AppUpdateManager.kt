package com.iproxy.app.core.update

import android.content.Context
import android.content.Intent
import android.os.Build
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
    data class Failed(val reason: String) : InstallLaunchResult()
}

/**
 * 应用自更新检查器与 APK 下载安装管理器
 *
 * 从 GitHub Releases 检查最新版本，按设备 CPU 架构自动匹配可安装的 APK 资产。
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
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            updatesDir.listFiles()?.forEach { old ->
                runCatching { old.delete() }
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
                error("下载的 APK 文件体积异常 (${partFile.length()} bytes)")
            }
            if (targetFile.exists()) targetFile.delete()
            if (!partFile.renameTo(targetFile)) {
                partFile.copyTo(targetFile, overwrite = true)
                partFile.delete()
            }
            targetFile
        }
    }

    fun launchApkInstaller(apkFile: File): InstallLaunchResult {
        if (!apkFile.exists()) {
            return InstallLaunchResult.Failed("安装包文件不存在")
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
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            InstallLaunchResult.Launched
        }.getOrElse { err ->
            InstallLaunchResult.Failed(err.message ?: "无法调起系统安装器")
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
        private const val GITHUB_OWNER = "JIULONG110"
        private const val GITHUB_REPO = "I"
        const val RELEASES_PAGE_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        private const val RELEASES_LIST_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases?per_page=5"

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

            for (abi in supportedAbis) {
                val match = installableApks.firstOrNull {
                    it.name.contains(abi, ignoreCase = true)
                }
                if (match != null) return match
            }

            return installableApks.firstOrNull {
                it.name.contains("universal", ignoreCase = true)
            } ?: installableApks.firstOrNull()
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
