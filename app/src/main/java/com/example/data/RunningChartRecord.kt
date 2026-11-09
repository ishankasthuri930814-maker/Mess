package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "running_chart_records")
data class RunningChartRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val vehicleNumber: String, // e.g. "KX 1493"
    val startMeter: Double,
    val endMeter: Double,
    val distanceKm: Double,
    val kmPerLiter: Double,
    val startFuel: Double,
    val addedFuel: Double,
    val consumedFuel: Double,
    val remainingFuel: Double,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
