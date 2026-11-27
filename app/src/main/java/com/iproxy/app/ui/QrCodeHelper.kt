package com.iproxy.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 二维码生成与识别辅助工具（基于 ZXing）
 */
object QrCodeHelper {

    private val decodeHints: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )

    /**
     * 生成高对比度二维码位图，用于节点分享
     */
    fun generateQrBitmap(content: String, sizePx: Int = 640): Bitmap? {
        if (content.isBlank()) return null
        return runCatching {
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val pixels = IntArray(sizePx * sizePx)
            for (y in 0 until sizePx) {
                val offset = y * sizePx
                for (x in 0 until sizePx) {
                    pixels[offset + x] = if (matrix.get(x, y)) Color.rgb(4, 11, 22) else Color.rgb(230, 244, 254)
                }
            }
            createBitmap(sizePx, sizePx).apply {
                setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
            }
        }.getOrNull()
    }

    /**
     * 从文件 / 相册 Uri 解码二维码文本
     */
    fun decodeFromUri(context: Context, uri: Uri): Result<String> {
        return runCatching {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            val decoded = decodeFromBitmap(bitmap)
            if (decoded.isNullOrBlank()) {
                error("未能从所选图片中识别出二维码")
            }
            decoded
        }
    }

    /**
     * 从相册或截图位图解码二维码文本（兼容大图缩放、硬件位图转换与暗色反色二维码）
     */
    fun decodeFromBitmap(bitmap: Bitmap): String? {
        return runCatching {
            val software = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
            val normalized = scaleDownIfTooLarge(software, maxDim = 1600)
            try {
                val width = normalized.width
                val height = normalized.height
                val pixels = IntArray(width * height)
                normalized.getPixels(pixels, 0, width, 0, 0, width, height)
                val source = RGBLuminanceSource(width, height, pixels)
                decodeLuminanceSource(source)
            } finally {
                if (normalized !== software && normalized !== bitmap) {
                    normalized.recycle()
                }
                if (software !== bitmap) {
                    software.recycle()
                }
            }
        }.getOrNull()
    }

    /**
     * 从 YUV_420_888 相机帧 Y 平面解码二维码文本
     */
    fun decodeFromYuvPlane(
        yBytes: ByteArray,
        width: Int,
        height: Int
    ): String? {
        return runCatching {
            val source = com.google.zxing.PlanarYUVLuminanceSource(
                yBytes,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )
            decodeLuminanceSource(source)
        }.getOrNull()
    }

    private fun decodeLuminanceSource(source: LuminanceSource): String? {
        val reader = MultiFormatReader().apply { setHints(decodeHints) }
        val normal = runCatching {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        }.getOrNull()
        if (!normal.isNullOrBlank()) return normal

        // 暗黑模式/反色二维码回退识别
        reader.reset()
        return runCatching {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.invert()))).text
        }.getOrNull()
    }

    private fun scaleDownIfTooLarge(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDim || longest <= 0) return bitmap
        val ratio = maxDim.toFloat() / longest.toFloat()
        val dstW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val dstH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return bitmap.scale(dstW, dstH)
    }
}
