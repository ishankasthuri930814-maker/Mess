package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val title: String = "",
    val message: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderRole: String = "OIC",
    val targetType: String = "STATION", // "ALL", "STATION", "USER"
    val targetStationCode: String = "",
    val targetUid: String = "",
    val priority: String = "NORMAL", // "NORMAL", "URGENT"
    val createdAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)
