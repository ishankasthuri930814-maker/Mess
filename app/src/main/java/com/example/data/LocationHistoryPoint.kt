package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class LocationHistoryPoint(
    @DocumentId val id: String = "",
    val officerId: String = "",
    val officerName: String = "",
    val oicStationCode: String = "",
    val dateKey: String = "", // YYYY-MM-DD
    val dayStationKey: String = "", // "${oicStationCode}_${dateKey}"
    val officerDayKey: String = "", // "${officerId}_${dateKey}"
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val accuracy: Float = 0f,
    val dutyType: String = "ON_DUTY", // "ON_DUTY" or "OFF_DUTY"
    val timestamp: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null
)
