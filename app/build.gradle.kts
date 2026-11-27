import java.net.HttpURLConnection
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.iproxy.app"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.iproxy.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 106
        versionName = "1.0.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.isFile) {
            keystorePropsFile.inputStream().use { load(it) }
        }
    }
    val repoDefaultKeystore = project.file("signing/iproxy.p12")
    val signingStorePath = System.getenv("ANDROID_KEYSTORE_PATH")
        ?: keystoreProps.getProperty("storeFile")
    val envStoreFile = signingStorePath?.let { path ->
        val f = file(path)
        if (f.isFile) f else rootProject.file(path).takeIf { it.isFile }
    }
    val useExternalSigning = envStoreFile != null &&
        !(System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: keystoreProps.getProperty("storePassword")).isNullOrBlank() &&
        !(System.getenv("ANDROID_KEY_ALIAS") ?: keystoreProps.getProperty("keyAlias")).isNullOrBlank() &&
        !(System.getenv("ANDROID_KEY_PASSWORD") ?: keystoreProps.getProperty("keyPassword")).isNullOrBlank()

    val signingStoreFile = if (useExternalSigning) envStoreFile else repoDefaultKeystore.takeIf { it.isFile }
    val signingStorePassword = if (useExternalSigning) {
        System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: keystoreProps.getProperty("storePassword")
    } else {
        "iproxy2026release"
    }
    val signingKeyAlias = if (useExternalSigning) {
        System.getenv("ANDROID_KEY_ALIAS") ?: keystoreProps.getProperty("keyAlias")
    } else {
        "iproxy"
    }
    val signingKeyPassword = if (useExternalSigning) {
        System.getenv("ANDROID_KEY_PASSWORD") ?: keystoreProps.getProperty("keyPassword")
    } else {
        "iproxy2026release"
    }
    val hasPersistentSigning = signingStoreFile != null

    signingConfigs {
        if (hasPersistentSigning) {
            create("appSigning") {
                storeFile = signingStoreFile
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasPersistentSigning) {
                signingConfig = signingConfigs.getByName("appSigning")
            }
        }
        debug {
            isMinifyEnabled = false
            if (hasPersistentSigning) {
                signingConfig = signingConfigs.getByName("appSigning")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

/**
 * 下载核心运行所需二进制资产：
 * 1. Xray-core Android AAR (2dust/AndroidLibXrayLite v26.11.26)
 * 2. Loyalsoldier GeoIP / GeoSite 路由规则数据库
 */
val downloadCoreAssets = tasks.register("downloadCoreAssets") {
    group = "setup"
    description = "自动下载 libv2ray.aar 与 geoip.dat / geosite.dat"

    val libsDir = layout.projectDirectory.dir("libs").asFile
    val assetsDir = layout.projectDirectory.dir("src/main/assets").asFile

    val targets = listOf(
        Triple(
            "https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.11.26/libv2ray.aar",
            File(libsDir, "libv2ray.aar"),
            10_000_000L
        ),
        Triple(
            "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat",
            File(assetsDir, "geoip.dat"),
            1_000_000L
        ),
        Triple(
            "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat",
            File(assetsDir, "geosite.dat"),
            1_000_000L
        )
    )

    outputs.files(targets.map { it.second })

    doLast {
        fun downloadWithRedirect(urlStr: String, dest: File, minBytes: Long) {
            if (dest.exists() && dest.length() >= minBytes) {
                logger.lifecycle("已存在有效资产，跳过下载: ${dest.name} (${dest.length()} bytes)")
                return
            }
            dest.parentFile.mkdirs()
            val partFile = File(dest.parentFile, "${dest.name}.part")
            var currentUrl = urlStr
            var redirects = 0
            while (redirects < 8) {
                val conn = (URI(currentUrl).toURL().openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 20_000
                    readTimeout = 120_000
                    setRequestProperty("User-Agent", "I-Android-Builder/1.0")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                        ?: error("重定向缺少 Location: $currentUrl")
                    currentUrl = URI(currentUrl).resolve(location).toString()
                    redirects++
                    conn.disconnect()
                    continue
                }
                if (code !in 200..299) {
                    conn.disconnect()
                    error("下载失败 ($code): $currentUrl")
                }
                logger.lifecycle("正在下载 ${dest.name} ...")
                conn.inputStream.use { input ->
                    partFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
                conn.disconnect()
                if (partFile.length() < minBytes) {
                    error("下载文件体积异常 (${partFile.length()} bytes): ${dest.name}")
                }
                if (dest.exists()) dest.delete()
                if (!partFile.renameTo(dest)) {
                    partFile.copyTo(dest, overwrite = true)
                    partFile.delete()
                }
                logger.lifecycle("下载完成: ${dest.name} (${dest.length()} bytes)")
                return
            }
            error("重定向次数过多: $urlStr")
        }

        targets.forEach { (url, file, minBytes) ->
            downloadWithRedirect(url, file, minBytes)
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadCoreAssets)
}

dependencies {
    // 核心代理引擎与 TUN 隧道
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    implementation(libs.hevtunnel)

    // AndroidX 基础与 Compose UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // 生命周期与协程
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // 存储与网络
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.mmkv.static)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)

    // 二维码扫码与生成
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Java 8+ API 脱糖支持
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // 单元测试与 UI 测试
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
