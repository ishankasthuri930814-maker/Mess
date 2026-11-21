package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.math.max

data class LiveMonitorCapability(
    val canStreamVideo: Boolean,
    val canStreamAudio: Boolean
) {
    val canStreamAny: Boolean get() = canStreamVideo || canStreamAudio

    fun describeModeSinhala(): String = when {
        canStreamVideo && canStreamAudio -> "සජීවී වීඩියෝ + ශ්‍රව්‍ය (Video & Audio)"
        canStreamVideo -> "සජීවී වීඩියෝ පමණි (Mic අවසර නැත)"
        canStreamAudio -> "සජීවී ශ්‍රව්‍ය පමණි (Camera අවසර නැත)"
        else -> "කැමරා / මයික්‍රොෆෝන අවසර ලබාදී නැත"
    }
}

object LiveMediaCodecUtils {

    const val AUDIO_SAMPLE_RATE = 16_000
    private const val MAX_VIDEO_DIMENSION = 480

    fun resolveMonitorCapability(
        isOnDuty: Boolean,
        hasCameraPermission: Boolean,
        hasMicPermission: Boolean
    ): LiveMonitorCapability {
        if (!isOnDuty) {
            return LiveMonitorCapability(canStreamVideo = false, canStreamAudio = false)
        }
        return LiveMonitorCapability(
            canStreamVideo = hasCameraPermission,
            canStreamAudio = hasMicPermission
        )
    }

    /**
     * Converts a CameraX YUV_420_888 ImageProxy to a compact rotated JPEG ByteArray (max 480px side).
     */
    fun imageProxyToCompactJpeg(image: ImageProxy, quality: Int = 46): Triple<ByteArray, Int, Int>? {
        val nv21 = yuv420888ToNv21(image) ?: return null
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val rawJpegOut = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 72, rawJpegOut)
        val rawBytes = rawJpegOut.toByteArray()

        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null
        return try {
            val rotation = image.imageInfo.rotationDegrees.toFloat()
            val maxSide = max(decoded.width, decoded.height).coerceAtLeast(1)
            val scale = if (maxSide > MAX_VIDEO_DIMENSION) {
                MAX_VIDEO_DIMENSION.toFloat() / maxSide.toFloat()
            } else {
                1f
            }
            val finalBitmap = if (rotation != 0f || scale != 1f) {
                val matrix = Matrix().apply {
                    if (scale != 1f) postScale(scale, scale)
                    if (rotation != 0f) postRotate(rotation)
                }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            } else {
                decoded
            }
            val out = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(25, 85), out)
            val w = finalBitmap.width
            val h = finalBitmap.height
            if (finalBitmap !== decoded) {
                finalBitmap.recycle()
            }
            Triple(out.toByteArray(), w, h)
        } finally {
            if (!decoded.isRecycled) {
                decoded.recycle()
            }
        }
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray? {
        if (image.planes.size < 3) return null
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4
        val nv21 = ByteArray(ySize + uvSize * 2)

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        var rowStride = image.planes[0].rowStride
        var pos = 0
        if (image.planes[0].pixelStride == 1) {
            if (rowStride == width) {
                yBuffer.get(nv21, 0, ySize)
                pos += ySize
            } else {
                var yRowOffset = 0
                for (row in 0 until height) {
                    yBuffer.position(yRowOffset)
                    yBuffer.get(nv21, pos, width)
                    pos += width
                    yRowOffset += rowStride
                }
            }
        } else {
            return null
        }

        rowStride = image.planes[2].rowStride
        val pixelStride = image.planes[2].pixelStride
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val vuIdx = row * rowStride + col * pixelStride
                nv21[pos++] = vBuffer.get(vuIdx)
                nv21[pos++] = uBuffer.get(vuIdx)
            }
        }
        return nv21
    }

    // --- Pure-Kotlin 4-bit IMA-ADPCM Audio Codec (4:1 ratio, 16-bit mono PCM) ---

    private val INDEX_TABLE = intArrayOf(
        -1, -1, -1, -1, 2, 4, 6, 8,
        -1, -1, -1, -1, 2, 4, 6, 8
    )

    private val STEP_TABLE = intArrayOf(
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
        19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
        50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
        130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
        337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
        876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
        2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
        5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
        15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
    )

    /**
     * Encodes 16-bit mono PCM samples into 4-bit IMA-ADPCM with a 4-byte state header.
     */
    fun encodeImaAdpcm(pcmSamples: ShortArray, sampleCount: Int = pcmSamples.size): ByteArray {
        val count = sampleCount.coerceIn(0, pcmSamples.size)
        if (count == 0) return ByteArray(4)
        var predictor = pcmSamples[0].toInt()
        var stepIndex = 0
        val payloadBytes = (count + 1) / 2
        val out = ByteArray(4 + payloadBytes)
        // 4-byte header: initial predictor (LE 16-bit), stepIndex, reserved 0
        out[0] = (predictor and 0xFF).toByte()
        out[1] = ((predictor shr 8) and 0xFF).toByte()
        out[2] = stepIndex.toByte()
        out[3] = 0

        var outPos = 4
        var lowNibble = true
        var currentByte = 0

        for (i in 0 until count) {
            val sample = pcmSamples[i].toInt()
            var diff = sample - predictor
            var code = 0
            if (diff < 0) {
                code = 8
                diff = -diff
            }
            val step = STEP_TABLE[stepIndex]
            var delta = step shr 3
            if (diff >= step) {
                code = code or 4
                diff -= step
                delta += step
            }
            val step2 = step shr 1
            if (diff >= step2) {
                code = code or 2
                diff -= step2
                delta += step2
            }
            val step4 = step shr 2
            if (diff >= step4) {
                code = code or 1
                delta += step4
            }
            predictor = if ((code and 8) != 0) predictor - delta else predictor + delta
            predictor = predictor.coerceIn(-32768, 32767)
            stepIndex = (stepIndex + INDEX_TABLE[code]).coerceIn(0, 88)

            if (lowNibble) {
                currentByte = code and 0x0F
                lowNibble = false
            } else {
                out[outPos++] = (currentByte or ((code and 0x0F) shl 4)).toByte()
                lowNibble = true
            }
        }
        if (!lowNibble && outPos < out.size) {
            out[outPos] = currentByte.toByte()
        }
        return out
    }

    /**
     * Decodes IMA-ADPCM bytes produced by [encodeImaAdpcm] back into 16-bit mono PCM samples.
     */
    fun decodeImaAdpcm(adpcmBytes: ByteArray, sampleCount: Int): ShortArray {
        if (adpcmBytes.size < 4 || sampleCount <= 0) return ShortArray(0)
        val maxPossible = (adpcmBytes.size - 4) * 2
        val count = sampleCount.coerceAtMost(maxPossible)
        var predictor = ((adpcmBytes[0].toInt() and 0xFF) or (adpcmBytes[1].toInt() shl 8)).toShort().toInt()
        var stepIndex = (adpcmBytes[2].toInt() and 0xFF).coerceIn(0, 88)
        val out = ShortArray(count)

        for (i in 0 until count) {
            val byteVal = adpcmBytes[4 + (i / 2)].toInt() and 0xFF
            val code = if ((i and 1) == 0) (byteVal and 0x0F) else ((byteVal shr 4) and 0x0F)
            val step = STEP_TABLE[stepIndex]
            var delta = step shr 3
            if ((code and 4) != 0) delta += step
            if ((code and 2) != 0) delta += step shr 1
            if ((code and 1) != 0) delta += step shr 2
            predictor = if ((code and 8) != 0) predictor - delta else predictor + delta
            predictor = predictor.coerceIn(-32768, 32767)
            stepIndex = (stepIndex + INDEX_TABLE[code]).coerceIn(0, 88)
            out[i] = predictor.toShort()
        }
        return out
    }
}
