package com.example.util

import java.util.Locale

object ChatAttachmentUtils {
    const val MAX_ATTACHMENT_BYTES = 10L * 1024L * 1024L // 10 MB strict limit
    const val CHUNK_RAW_BYTES = 393_216 // 384 KiB (divisible by 3 -> 512 KiB Base64 per chunk doc)
    const val RETENTION_MS = 2L * 24L * 60L * 60L * 1000L // 2 days (48 hours)
    const val MAX_CHUNK_COUNT = 40

    fun isWithinSizeLimit(sizeBytes: Long): Boolean {
        return sizeBytes in 1L..MAX_ATTACHMENT_BYTES
    }

    fun computeExpiresAtMs(createdAtMs: Long): Long {
        val base = if (createdAtMs > 0L) createdAtMs else System.currentTimeMillis()
        return base + RETENTION_MS
    }

    fun isExpired(
        createdAtMs: Long,
        expiresAtMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (expiresAtMs > 0L && nowMs >= expiresAtMs) return true
        if (createdAtMs > 0L && (nowMs - createdAtMs) >= RETENTION_MS) return true
        return false
    }

    fun inferMessageType(mimeType: String, fileName: String): String {
        val lowerMime = mimeType.trim().lowercase(Locale.US)
        val ext = fileName.substringAfterLast('.', "").trim().lowercase(Locale.US)
        return when {
            lowerMime == "application/pdf" || ext == "pdf" -> "PDF"
            lowerMime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif") -> "IMAGE"
            lowerMime.startsWith("audio/") || ext in setOf("m4a", "mp3", "wav", "ogg", "aac", "amr", "opus", "3gpp") -> "AUDIO"
            else -> "FILE"
        }
    }

    fun isVideoFile(mimeType: String, fileName: String): Boolean {
        val lowerMime = mimeType.trim().lowercase(Locale.US)
        val ext = fileName.substringAfterLast('.', "").trim().lowercase(Locale.US)
        return lowerMime.startsWith("video/") || ext in setOf("mp4", "mkv", "3gp", "webm", "mov", "avi")
    }

    fun isTextFile(mimeType: String, fileName: String): Boolean {
        val lowerMime = mimeType.trim().lowercase(Locale.US)
        val ext = fileName.substringAfterLast('.', "").trim().lowercase(Locale.US)
        return lowerMime.startsWith("text/") ||
            lowerMime == "application/json" ||
            lowerMime == "application/xml" ||
            ext in setOf("txt", "csv", "json", "xml", "log", "md", "html", "htm", "ini", "yaml", "yml")
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB", mb)
    }

    fun formatDuration(durationMs: Long): String {
        val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
        val mins = totalSec / 60L
        val secs = totalSec % 60L
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    fun formatRemainingRetention(expiresAtMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (expiresAtMs <= 0L) return "පැය 48කින් ස්වයංක්‍රීයව ඉවත් වේ"
        val remMs = (expiresAtMs - nowMs).coerceAtLeast(0L)
        val totalHours = remMs / (60L * 60L * 1000L)
        val mins = (remMs % (60L * 60L * 1000L)) / 60_000L
        return when {
            remMs <= 0L -> "ඉවත් වෙමින් පවතී"
            totalHours >= 1L -> "තව පැය $totalHours විනාඩි $mins කින් ස්වයංක්‍රීයව ඉවත් වේ"
            else -> "තව විනාඩි $mins කින් ස්වයංක්‍රීයව ඉවත් වේ"
        }
    }

    fun sanitizeFileName(rawName: String, fallback: String = "attachment.bin"): String {
        val cleaned = rawName.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (cleaned.isBlank() || cleaned == "." || cleaned == "..") fallback else cleaned.take(120)
    }

    fun splitByteArray(data: ByteArray, chunkSize: Int = CHUNK_RAW_BYTES): List<ByteArray> {
        if (data.isEmpty() || chunkSize <= 0) return emptyList()
        val chunks = ArrayList<ByteArray>((data.size + chunkSize - 1) / chunkSize)
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + chunkSize, data.size)
            chunks.add(data.copyOfRange(offset, end))
            offset = end
        }
        return chunks
    }
}
