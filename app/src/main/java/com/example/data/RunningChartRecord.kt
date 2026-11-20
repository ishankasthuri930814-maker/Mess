package com.example.data

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
@Entity(tableName = "running_chart_records")
data class RunningChartRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val officerId: String = "",
    val oicStationCode: String = "",
    val date: String = "", // YYYY-MM-DD
    val vehicleNumber: String = "", // e.g. "KX 1493"
    val startMeter: Double = 0.0,
    val endMeter: Double = 0.0,
    val distanceKm: Double = 0.0,
    val kmPerLiter: Double = 0.0,
    val startFuel: Double = 0.0,
    val addedFuel: Double = 0.0,
    val consumedFuel: Double = 0.0,
    val remainingFuel: Double = 0.0,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    @ServerTimestamp @Ignore var createdAt: Timestamp? = null
}

