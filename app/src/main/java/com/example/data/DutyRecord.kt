package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "duty_records")
data class DutyRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "ON_DUTY" or "OFF_DUTY"
    val timestamp: Long = System.currentTimeMillis(),
    val ibPage: String, // Information book page
    val ibParagraph: String, // Paragraph
    val dutyNature: String, // Nature of duty
    val remarks: String = "",
    val photoPath: String? = null, // Information book page photo
    val scenePhotoPath: String? = null, // Incident / Scene photo (සිද්ධියේ හෝ ස්ථානයේ ඡායාරූපය)
    val dutyDurationMinutes: Long = 0L, // Shift duration in minutes
    val originalSizeKb: Int = 0,
    val compressedSizeKb: Int = 0
)
