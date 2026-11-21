package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class StationChatMessage(
    @DocumentId val id: String = "",
    val oicStationCode: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderRole: String = "OFFICER", // "OIC", "ADMIN_BRANCH", "OFFICER"
    val senderRank: String = "",
    val senderBadge: String = "",
    val message: String = "",
    val messageType: String = "TEXT", // "TEXT", "AUDIO", "IMAGE", "PDF", "FILE"
    val attachmentName: String = "",
    val attachmentMimeType: String = "",
    val attachmentSizeBytes: Long = 0L,
    val attachmentDurationMs: Long = 0L,
    val attachmentChunkCount: Int = 0,
    val expiresAtMs: Long = 0L,
    val createdAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun effectiveTimestampMs(): Long = createdAt?.toDate()?.time ?: createdAtMs

    fun hasAttachment(): Boolean = messageType != "TEXT" && attachmentChunkCount > 0

    fun uniqueKey(): String = id.ifBlank { "${senderUid}_${createdAtMs}_${attachmentName}" }

    fun formatSenderHeader(): String {
        val cleanName = senderName.ifBlank { "නිලධාරී" }
        return when (senderRole.uppercase()) {
            "OIC" -> "ස්ථානාධිපති (OIC) • $cleanName"
            "ADMIN_BRANCH" -> "පාලන අංශය (Admin) • $cleanName"
            else -> {
                val rankShort = formatShortRank(senderRank)
                val cleanBadge = senderBadge.trim()
                val rolePart = when {
                    rankShort.isNotBlank() && cleanBadge.isNotBlank() -> "$rankShort ($cleanBadge)"
                    rankShort.isNotBlank() -> rankShort
                    cleanBadge.isNotBlank() -> "No: $cleanBadge"
                    else -> "නිලධාරී"
                }
                "$rolePart • $cleanName"
            }
        }
    }

    companion object {
        fun formatShortRank(rank: String): String {
            val trimmed = rank.trim()
            if (trimmed.isBlank()) return ""
            val parenMatch = Regex("\\(([A-Za-z]{1,8})\\)").find(trimmed)
            return parenMatch?.groupValues?.getOrNull(1)?.uppercase() ?: trimmed
        }
    }
}
