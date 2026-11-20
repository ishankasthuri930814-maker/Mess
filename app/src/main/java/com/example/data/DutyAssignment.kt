package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class DutyAssignment(
    val id: String = "",
    val officerId: String = "",
    val officerName: String = "",
    val officerBadge: String = "",
    val officerRank: String = "",
    val officerPhone: String = "",
    val oicStationCode: String = "",
    val dateKey: String = "", // "YYYY-MM-DD"
    val dutyNature: String = "", // e.g. "Station Reserve (උපසේවා)", "Mobile Patrol (මුරසංචාරය)", etc.
    val shift: String = "", // e.g. "Day Shift (08:00 - 16:00)"
    val remarks: String = "",
    val assignedByUid: String = "",
    val assignedByName: String = "",
    val status: String = "ASSIGNED", // "ASSIGNED", "COMPLETED", "MODIFIED"
    val createdAtMs: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null
)
