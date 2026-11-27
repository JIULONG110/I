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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iproxy.app.ui.QrCodeHelper
import com.iproxy.app.ui.components.ProtoTextPrimary
import com.iproxy.app.ui.components.ProtoTextSecondary
import com.iproxy.app.ui.components.prototypePrimaryButtonColors
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 相机扫码 + 相册选图识别二维码页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    onScanned: (String) -> Unit,
    onScanError: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = ProtoTextPrimary,
        topBar = {
            TopAppBar(
                title = { Text("扫描节点或订阅二维码", color = ProtoTextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xAA050A14),
                    titleContentColor = ProtoTextPrimary,
                    navigationIconContentColor = ProtoTextPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "需要相机权限以扫描二维码，您也可以直接从相册图片中识别二维码。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProtoTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        colors = prototypePrimaryButtonColors(),
                        shape = RoundedCornerShape(14.dp),
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                    ) {
                        Text("授予相机权限")
                    }
                }
            }

            Button(
                colors = prototypePrimaryButtonColors(),
                shape = RoundedCornerShape(14.dp),
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
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("从相册选择二维码图片")
            }
        }
    }
}
