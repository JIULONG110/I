package com.iproxy.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iproxy.app.ui.QrCodeHelper
import com.iproxy.app.ui.components.BlueConstellationParticlesCanvas
import com.iproxy.app.ui.components.GlassActionButton
import com.iproxy.app.ui.components.GlassEmptyStateCard
import com.iproxy.app.ui.components.GlassSmallIconButton
import com.iproxy.app.ui.components.ProtoTextPrimary
import com.iproxy.app.ui.components.drawGlassCapsule3D
import com.iproxy.app.ui.components.rememberReduceMotionState
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 相机扫码 + 相册选图识别二维码页面（3D 蓝色玻璃取景框与悬浮顶栏）
 */
@Composable
fun QrScanScreen(
    onScanned: (String) -> Unit,
    onScanError: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reduceMotion = rememberReduceMotionState()
    val handled = remember { AtomicBoolean(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        QrCodeHelper.decodeFromUri(context, uri)
            .onSuccess { onScanned(it) }
            .onFailure { onScanError(it.message ?: "读取图片失败") }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // 3D 玻璃顶栏（带持续蓝色星链粒子流）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(18.dp))
                .drawBehind {
                    drawGlassCapsule3D(cornerRadiusDp = 18.dp, active = false, elevated = true)
                }
        ) {
            BlueConstellationParticlesCanvas(
                reduceMotion = reduceMotion,
                densityMultiplier = 0.62f,
                intensity = 0.72f,
                modifier = Modifier.matchParentSize()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassSmallIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    sizeDp = 36.dp,
                    cornerRadius = 12.dp,
                    onClick = onBack
                )
                Text(
                    text = "扫描节点或订阅二维码",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProtoTextPrimary
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (hasCameraPermission) {
                val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
                DisposableEffect(Unit) {
                    onDispose { analyzerExecutor.shutdown() }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            val cameraProvider = providerFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                                try {
                                    if (!handled.get()) {
                                        val plane = imageProxy.planes.firstOrNull()
                                        if (plane != null) {
                                            val buffer = plane.buffer
                                            val bytes = ByteArray(buffer.remaining())
                                            buffer.get(bytes)
                                            val result = QrCodeHelper.decodeFromYuvPlane(
                                                yBytes = bytes,
                                                width = imageProxy.width,
                                                height = imageProxy.height
                                            )
                                            if (!result.isNullOrBlank() && handled.compareAndSet(false, true)) {
                                                ContextCompat.getMainExecutor(ctx).execute {
                                                    onScanned(result)
                                                }
                                            }
                                        }
                                    }
                                } finally {
                                    imageProxy.close()
                                }
                            }
                            runCatching {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    }
                )

                // 取景四角发光蓝框（不遮挡中间摄像头识别画面）
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(240.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val len = 34.dp.toPx()
                        val stroke = 3.2.dp.toPx()
                        val c = Color(0xFF4BE8FF)
                        // 左上
                        drawLine(c, Offset(0f, 0f), Offset(len, 0f), strokeWidth = stroke, cap = StrokeCap.Round)
                        drawLine(c, Offset(0f, 0f), Offset(0f, len), strokeWidth = stroke, cap = StrokeCap.Round)
                        // 右上
                        drawLine(c, Offset(size.width, 0f), Offset(size.width - len, 0f), strokeWidth = stroke, cap = StrokeCap.Round)
                        drawLine(c, Offset(size.width, 0f), Offset(size.width, len), strokeWidth = stroke, cap = StrokeCap.Round)
                        // 左下
                        drawLine(c, Offset(0f, size.height), Offset(len, size.height), strokeWidth = stroke, cap = StrokeCap.Round)
                        drawLine(c, Offset(0f, size.height), Offset(0f, size.height - len), strokeWidth = stroke, cap = StrokeCap.Round)
                        // 右下
                        drawLine(c, Offset(size.width, size.height), Offset(size.width - len, size.height), strokeWidth = stroke, cap = StrokeCap.Round)
                        drawLine(c, Offset(size.width, size.height), Offset(size.width, size.height - len), strokeWidth = stroke, cap = StrokeCap.Round)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassEmptyStateCard(
                        title = "未授予相机权限",
                        subtitle = "需要相机权限以实时扫描二维码，您也可以直接从相册图片中识别节点配置。"
                    ) {
                        GlassActionButton(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                        ) {
                            Text("授予相机权限", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            GlassActionButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("从相册选择二维码图片", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
