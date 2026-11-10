package com.example.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
@Entity(tableName = "mess_records")
data class MessRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val officerId: String = "",
    val oicEmail: String = "",
    val date: String = "", // yyyy-MM-dd
    val mealType: String = "", // "BREAKFAST", "LUNCH", "DINNER"
    val isTaken: Boolean = true,
    val mealNotes: String = "",
    val price: Double = 0.0,
    val photoPath: String? = null,
    val compressedSizeKb: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    @ServerTimestamp @Ignore var createdAt: Timestamp? = null
}
