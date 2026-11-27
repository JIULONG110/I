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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iproxy.app.BuildConfig
import com.iproxy.app.core.update.AppUpdateInfo
import com.iproxy.app.core.update.InstallLaunchResult
import com.iproxy.app.core.util.FormatUtils
import com.iproxy.app.ui.theme.CyanAccent
import com.iproxy.app.ui.theme.ErrorRed
import com.iproxy.app.ui.theme.LocalLiquidPalette

/**
 * 黑曜石液态开屏层：展示重新设计的黑色 Logo，并在发现新版本时无缝衔接更新弹窗
 */
@Composable
fun SplashBrandOverlay(
    visible: Boolean,
    isCheckingUpdate: Boolean
) {
    var animStarted by remember { mutableStateOf(false) }
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
                        0.0f to Color(0xFF050912),
                        0.58f to Color(0xFF08111F),
                        1.0f to Color(0xFF050A14)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.scale(logoScale)
            ) {
                BlackBrandLogo(
                    size = 96.dp,
                    accentGlow = Color(0xFF53CAFF)
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "I",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFEFF7FF),
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isCheckingUpdate) "正在检查版本更新..." else "极简加密代理",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9FB2C9)
                )
            }
        }
    }
}

/**
 * 开屏 / 手动触发的软件更新液态弹窗
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
    val palette = LocalLiquidPalette.current
    val shape = RoundedCornerShape(26.dp)

    Dialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = !isDownloading
        )
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(
                        Brush.linearGradient(
                            listOf(palette.glassElevatedStart, palette.glassElevatedEnd)
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(palette.glassBorderTop, palette.glassBorderBottom)
                        ),
                        shape = shape
                    )
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BlackBrandLogo(size = 44.dp, accentGlow = CyanAccent)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "发现新版本",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} → ${updateInfo.tagName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
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
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (updateInfo.releaseNotes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = updateInfo.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (downloadProgress != null) {
                        LinearProgressIndicator(
                            progress = { downloadProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "正在下载安装包 ${(downloadProgress * 100).toInt()}%...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "正在连接下载服务器...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (signatureMismatch != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ErrorRed.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!downloadError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = downloadError,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (signatureMismatch != null && hasPendingInstallApk) {
                    OutlinedButton(
                        onClick = onExportBackup,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("1. 复制节点与配置备份到剪贴板")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onSaveApkAndUninstall,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("2. 保存安装包到「下载」并卸载旧版")
                    }
                } else if (hasPendingInstallApk) {
                    Button(
                        onClick = onRetryInstall,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("立即安装已下载安装包")
                    }
                } else if (updateInfo.apkAsset != null) {
                    Button(
                        onClick = onDownloadAndInstall,
                        enabled = !isDownloading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isDownloading) "正在下载..." else "立即下载更新")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onOpenReleasePage,
                        enabled = !isDownloading
                    ) {
                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("浏览器下载")
                    }
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isDownloading
                    ) {
                        Text("暂不更新")
                    }
                }
            }
        }
    }
}
