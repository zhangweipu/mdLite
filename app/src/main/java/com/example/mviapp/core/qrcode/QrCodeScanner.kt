package com.example.mviapp.core.qrcode

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 二维码扫描工具
 *
 * 使用 Google ML Kit 识别本地图片中的二维码。
 * 支持从相册选择的图片 Uri 中识别二维码内容。
 */
@Singleton
class QrCodeScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val scanner = BarcodeScanning.getClient(
        com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    /**
     * 从图片 Uri 中识别二维码
     *
     * @param imageUri 图片的 content:// Uri
     * @return 识别到的二维码文本内容，识别失败或无二维码时返回 null
     */
    suspend fun scanFromUri(imageUri: Uri): String? {
        val bitmap = loadBitmap(imageUri) ?: return null
        return scanFromBitmap(bitmap)
    }

    /**
     * 从 Bitmap 中识别二维码
     */
    suspend fun scanFromBitmap(bitmap: Bitmap): String? = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val result = barcodes.firstOrNull()?.rawValue
                cont.resume(result)
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return runCatching {
            // 先尝试直接解码，自动压缩避免 OOM
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            inputStream.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        }.getOrNull()
    }
}
