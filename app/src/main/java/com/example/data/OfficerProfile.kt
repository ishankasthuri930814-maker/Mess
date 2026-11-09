package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "officer_profile")
data class OfficerProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val rank: String = "Police Constable (PC)",
    val badgeNumber: String = "",
    val station: String = "",
    val registeredAt: Long = System.currentTimeMillis()
)
