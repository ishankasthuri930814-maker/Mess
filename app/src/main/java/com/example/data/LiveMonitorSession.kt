package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class LiveMonitorSession(
    val sessionId: String = "",
    val oicStationCode: String = "",
    val oicUid: String = "",
    val oicName: String = "",
    val officerUid: String = "",
    val officerName: String = "",
    val status: String = "REQUESTED", // "REQUESTED", "ACTIVE", "NEEDS_FOREGROUND", "ENDED"
    val requestedCamera: Boolean = false,
    val requestedAudio: Boolean = false,
    val cameraActive: Boolean = false,
    val micActive: Boolean = false,
    val lensFacing: String = "BACK", // "BACK", "FRONT"
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val expiresAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LiveVideoSlot(
    val slotIndex: Int = 0,
    val seq: Long = 0L,
    val jpegBase64: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val timestampMs: Long = 0L,
    val officerUid: String = "",
    val oicUid: String = "",
    val oicStationCode: String = ""
)

@Keep
@IgnoreExtraProperties
data class LiveAudioChunk(
    val slotIndex: Int = 0,
    val seq: Long = 0L,
    val adpcmBase64: String = "",
    val sampleRate: Int = 16000,
    val sampleCount: Int = 0,
    val timestampMs: Long = 0L,
    val officerUid: String = "",
    val oicUid: String = "",
    val oicStationCode: String = ""
)
