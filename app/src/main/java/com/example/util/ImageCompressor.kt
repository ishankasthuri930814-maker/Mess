package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class CompressionResult(
    val filePath: String,
    val originalSizeKb: Int,
    val compressedSizeKb: Int,
    val savingsPercent: Int
)

object ImageCompressor {

    private const val MAX_DIMENSION = 1280
    private const val COMPRESSION_QUALITY = 75

    suspend fun compressAndSave(context: Context, imageUri: Uri, prefix: String = "doc"): CompressionResult? =
        withContext(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver

                // 1. Calculate original size
                var originalSizeBytes = 0L
                contentResolver.openAssetFileDescriptor(imageUri, "r")?.use {
                    originalSizeBytes = it.length
                }
                if (originalSizeBytes <= 0) {
                    contentResolver.openInputStream(imageUri)?.use { input ->
                        originalSizeBytes = input.available().toLong()
                    }
                }
                val originalKb = max(1, (originalSizeBytes / 1024).toInt())

                // 2. Decode bounds to compute inSampleSize
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                contentResolver.openInputStream(imageUri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                }

                val originalWidth = options.outWidth
                val originalHeight = options.outHeight
                if (originalWidth <= 0 || originalHeight <= 0) return@withContext null

                var sampleSize = 1
                val maxDim = max(originalWidth, originalHeight)
                while (maxDim / (sampleSize * 2) >= MAX_DIMENSION) {
                    sampleSize *= 2
                }

                // 3. Decode scaled bitmap
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565 // Memory efficient
                }

                val sampledBitmap: Bitmap = contentResolver.openInputStream(imageUri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, decodeOptions)
                } ?: return@withContext null

                // 4. Handle EXIF Orientation
                val orientation = getOrientation(context, imageUri)
                val rotatedBitmap = rotateBitmap(sampledBitmap, orientation)

                // 5. Final dimension scale down if still exceeds MAX_DIMENSION
                val finalWidth = rotatedBitmap.width
                val finalHeight = rotatedBitmap.height
                val finalBitmap = if (finalWidth > MAX_DIMENSION || finalHeight > MAX_DIMENSION) {
                    val scale = MAX_DIMENSION.toFloat() / max(finalWidth, finalHeight)
                    val targetWidth = (finalWidth * scale).roundToInt()
                    val targetHeight = (finalHeight * scale).roundToInt()
                    val scaled = Bitmap.createScaledBitmap(rotatedBitmap, targetWidth, targetHeight, true)
                    if (scaled != rotatedBitmap) rotatedBitmap.recycle()
                    scaled
                } else {
                    rotatedBitmap
                }

                // 6. Save compressed image into internal directory
                val outputDir = File(context.filesDir, "police_records").apply {
                    if (!exists()) mkdirs()
                }
                val outputFile = File(outputDir, "${prefix}_${System.currentTimeMillis()}.jpg")

                FileOutputStream(outputFile).use { outStream ->
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, outStream)
                    outStream.flush()
                }
                finalBitmap.recycle()

                val compressedKb = max(1, (outputFile.length() / 1024).toInt())
                val savings = if (originalKb > compressedKb) {
                    (((originalKb - compressedKb).toFloat() / originalKb) * 100).roundToInt()
                } else {
                    0
                }

                CompressionResult(
                    filePath = outputFile.absolutePath,
                    originalSizeKb = originalKb,
                    compressedSizeKb = compressedKb,
                    savingsPercent = savings
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    private fun getOrientation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
                val exif = ExifInterface(input)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}
