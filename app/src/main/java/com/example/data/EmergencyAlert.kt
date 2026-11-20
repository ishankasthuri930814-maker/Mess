package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class EmergencyAlert(
    @DocumentId val id: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderRank: String = "",
    val senderBadge: String = "",
    val senderRole: String = "OFFICER", // "OIC" or "OFFICER"
    val senderPhone: String = "",
    val oicStationCode: String = "",
    val message: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val radiusKm: Double = 10.0,
    val status: String = "ACTIVE", // "ACTIVE" or "RESOLVED"
    val createdAtMs: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null
)
