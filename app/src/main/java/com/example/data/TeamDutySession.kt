package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class TeamDutySession(
    val sessionId: String = "",
    val joinCode: String = "",
    val leaderUid: String = "",
    val leaderName: String = "",
    val leaderBadge: String = "",
    val leaderRank: String = "",
    val oicStationCode: String = "",
    val dutyNature: String = "",
    val ibPage: String = "",
    val ibParagraph: String = "",
    val remarks: String = "",
    val status: String = "ACTIVE", // "ACTIVE" or "ENDED"
    val startedAtMs: Long = System.currentTimeMillis(),
    val endedAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TeamDutyMember(
    val uid: String = "",
    val displayName: String = "",
    val rank: String = "",
    val badgeNumber: String = "",
    val phoneNumber: String = "",
    val roleInTeam: String = "MEMBER", // "LEADER" or "MEMBER"
    val status: String = "ACTIVE", // "ACTIVE", "LEFT", "ENDED_BY_LEADER"
    val joinedAtMs: Long = System.currentTimeMillis()
)
