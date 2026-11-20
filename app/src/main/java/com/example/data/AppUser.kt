package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class AppUser(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = "OFFICER", // "OFFICER", "OIC", "ADMIN_BRANCH"
    val status: String = "PENDING", // "PENDING", "APPROVED", "REJECTED"
    val oicEmail: String = "",
    val oicStationCode: String? = null,
    val stationName: String = "",
    val badgeNumber: String = "",
    val rank: String = "",
    val phoneNumber: String = "",
    val address: String = "",
    val fcmToken: String = "",
    @get:PropertyName("isOnDuty")
    @field:PropertyName("isOnDuty")
    val isOnDuty: Boolean = false,
    val lastLocation: Map<String, Any>? = null,
    val lastOffDutyLocation: Map<String, Any>? = null,
    val adminSecretSeed: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

