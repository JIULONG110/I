package com.iproxy.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iproxy.app.BuildConfig
import com.iproxy.app.core.update.AppUpdateInfo
import com.iproxy.app.core.update.InstallLaunchResult
import com.iproxy.app.core.util.FormatUtils
import com.iproxy.app.ui.theme.CyanAccent
import com.iproxy.app.ui.theme.ErrorRed

/**
 * 3D 深海蓝玻璃开屏层：带高密流动蓝色星链粒子流与黑曜石光球 Logo，发现新版本时无缝衔接更新弹窗
 */
@Composable
fun SplashBrandOverlay(
    visible: Boolean,
    isCheckingUpdate: Boolean
) {
    var animStarted by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotionState()
    LaunchedEffect(Unit) {
        animStarted = true
    }
    val logoScale by animateFloatAsState(
        targetValue = if (animStarted) 1f else 0.86f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "splashLogoScale"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(320)) + scaleOut(targetScale = 1.04f, animationSpec = tween(320))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0xFF050814),
                        0.58f to Color(0xFF080E1E),
                        1.0f to Color(0xFF050914)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.scale(logoScale)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 132.dp, height = 216.dp)
                        .clip(RoundedCornerShape(66.dp))
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color(0xF5110E24),
                                0.55f to Color(0xFA080716),
                                1.0f to Color(0xFC070A1A)
                            )
                        )
                        .border(
                            width = 1.6.dp,
                            brush = Brush.linearGradient(
                                listOf(Color(0xE8E6D6FF), Color(0xB8C74EFF), Color(0xC94BE8FF))
                            ),
                            shape = RoundedCornerShape(66.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    BlueConstellationParticlesCanvas(
                        reduceMotion = reduceMotion,
                        densityMultiplier = 0.85f,
                        intensity = 1.05f,
                        columnConcentrated = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .clip(RoundedCornerShape(62.dp))
                    )
                    BlackBrandLogo(
                        size = 84.dp,
                        accentGlow = Color(0xFFB56CFF)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "I",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFF4EFFF),
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isCheckingUpdate) "正在检查版本更新..." else "极简加密代理",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA99FD2)
                )
            }
        }
    }
}

/**
 * 开屏 / 手动触发的软件更新 3D 蓝色实体玻璃弹窗
 */
@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    isDownloading: Boolean,
    downloadProgress: Float?,
    downloadError: String?,
    hasPendingInstallApk: Boolean,
    signatureMismatch: InstallLaunchResult.SignatureMismatch?,
    onDownloadAndInstall: () -> Unit,
    onRetryInstall: () -> Unit,
    onExportBackup: () -> Unit,
    onSaveApkAndUninstall: () -> Unit,
    onOpenReleasePage: () -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220))
    ) {
        GlassDialog(
            onDismissRequest = { if (!isDownloading) onDismiss() },
            title = "发现新版本 ${updateInfo.tagName}",
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = !isDownloading,
            headerExtra = {
                BlackBrandLogo(size = 34.dp, accentGlow = CyanAccent)
            },
            content = {
                Text(
                    text = "v${BuildConfig.VERSION_NAME} → ${updateInfo.tagName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF4BE8FF),
                    fontWeight = FontWeight.SemiBold
                )

                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    particleCount = 18
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = updateInfo.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ProtoTextPrimary
                        )
                        if (updateInfo.releaseNotes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = updateInfo.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = ProtoTextMuted
                            )
                        }
                        updateInfo.apkAsset?.let { asset ->
                            Spacer(modifier = Modifier.height(8.dp))
                            val sizeLabel = if (asset.sizeBytes > 0) {
                                " (${FormatUtils.formatBytes(asset.sizeBytes)})"
                            } else ""
                            Text(
                                text = "匹配安装包: ${asset.name}$sizeLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF79D1FF)
                            )
                        }
                    }
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(6.dp))
                    GlassLoadingBar(progress = downloadProgress)
                    Text(
                        text = if (downloadProgress != null) {
                            "正在下载安装包 ${(downloadProgress * 100).toInt()}%..."
                        } else {
                            "正在连接下载服务器..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = ProtoTextMuted
                    )
                }

                if (signatureMismatch != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ErrorRed.copy(alpha = 0.14f))
                            .border(1.dp, ErrorRed.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "签名不一致，无法直接覆盖安装",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = ErrorRed
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "当前签名 (${signatureMismatch.installedCertShort}...) 与新版 (${signatureMismatch.archiveCertShort}...) 不同。" +
                                    "请先复制节点备份，再保存安装包到「下载/I」并卸载旧版重装；升级到统一签名版后，今后更新均可直接覆盖安装。",
                                style = MaterialTheme.typography.bodySmall,
                                color = ProtoTextMuted
                            )
                        }
                    }
                }

                if (!downloadError.isNullOrBlank()) {
                    Text(
                        text = downloadError,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed
                    )
                }

                if (signatureMismatch != null && hasPendingInstallApk) {
                    GlassSecondaryButton(
                        onClick = onExportBackup,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("1. 复制节点与配置备份到剪贴板")
                    }
                    GlassActionButton(
                        onClick = onSaveApkAndUninstall,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("2. 保存安装包到「下载」并卸载旧版", fontWeight = FontWeight.SemiBold)
                    }
                } else if (hasPendingInstallApk) {
                    GlassActionButton(
                        onClick = onRetryInstall,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("立即安装已下载安装包", fontWeight = FontWeight.SemiBold)
                    }
                } else if (updateInfo.apkAsset != null) {
                    GlassActionButton(
                        onClick = onDownloadAndInstall,
                        enabled = !isDownloading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isDownloading) "正在下载..." else "立即下载更新", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            buttons = {
                GlassSecondaryButton(
                    onClick = onOpenReleasePage,
                    enabled = !isDownloading
                ) {
                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("浏览器下载")
                }
                GlassGhostButton(
                    onClick = onDismiss,
                    enabled = !isDownloading
                ) {
                    Text("暂不更新")
                }
            }
        )
    }
}
