package com.example.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
@Entity(tableName = "duty_records")
data class DutyRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val officerId: String = "",
    val oicStationCode: String = "",
    val type: String = "", // "ON_DUTY", "OFF_DUTY", "EXTRA_DUTY"
    val timestamp: Long = System.currentTimeMillis(),
    val ibPage: String = "",
    val ibParagraph: String = "",
    val dutyNature: String = "",
    val remarks: String = "",
    val photoPath: String? = null,
    val scenePhotoPath: String? = null,
    val dutyDurationMinutes: Long = 0L,
    val originalSizeKb: Int = 0,
    val compressedSizeKb: Int = 0
) {
    @ServerTimestamp @Ignore var createdAt: Timestamp? = null
}
