package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mess_records")
data class MessRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // yyyy-MM-dd
    val mealType: String, // "BREAKFAST", "LUNCH", "DINNER"
    val isTaken: Boolean = true,
    val mealNotes: String = "",
    val photoPath: String? = null,
    val compressedSizeKb: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
