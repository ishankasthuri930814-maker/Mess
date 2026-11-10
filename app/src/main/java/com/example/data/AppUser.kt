package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class AppUser(
    @DocumentId val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = "OFFICER", // "OFFICER", "OIC"
    val status: String = "PENDING", // "PENDING", "APPROVED", "REJECTED"
    val oicEmail: String = "",
    val isOnDuty: Boolean = false,
    val lastLocation: Map<String, Any>? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
