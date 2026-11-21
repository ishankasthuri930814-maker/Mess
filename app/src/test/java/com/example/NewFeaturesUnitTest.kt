package com.example

import com.example.data.DutyRecord
import com.example.data.LocationHistoryPoint
import com.example.data.StationChatMessage
import com.example.util.DateTimeUtils
import com.example.util.GeoRouteUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewFeaturesUnitTest {

    @Test
    fun filterAndSmoothTrail_rejectsTeleportSpikesAndSmoothsCorners() {
        val t0 = 1_700_000_000_000L
        val points = listOf(
            LocationHistoryPoint(id = "1", lat = 6.92700, lng = 79.86100, timestamp = t0),
            LocationHistoryPoint(id = "2", lat = 6.92750, lng = 79.86100, timestamp = t0 + 10_000L),
            // Impossible GPS teleport ~15km in 2 seconds -> must be filtered out
            LocationHistoryPoint(id = "spike", lat = 7.05000, lng = 79.95000, timestamp = t0 + 12_000L),
            LocationHistoryPoint(id = "3", lat = 6.92750, lng = 79.86150, timestamp = t0 + 20_000L),
            LocationHistoryPoint(id = "4", lat = 6.92800, lng = 79.86180, timestamp = t0 + 30_000L)
        )

        val smoothed = GeoRouteUtils.filterAndSmoothTrail(points)
        assertTrue("Smoothed trail should add intermediate curve points", smoothed.size > 4)
        assertEquals(6.92700, smoothed.first().first, 0.00001)
        assertEquals(79.86100, smoothed.first().second, 0.00001)
        assertEquals(6.92800, smoothed.last().first, 0.00001)
        assertEquals(79.86180, smoothed.last().second, 0.00001)
        assertFalse(
            "GPS spike coordinate must not appear in smoothed trail",
            smoothed.any { it.first > 7.0 }
        )
    }

    @Test
    fun extractStayLocations_identifiesLongStaysOverTwentyMinutes() {
        val t0 = 1_700_000_000_000L
        val points = listOf(
            // Stay A: 10 minutes (< 20 min threshold)
            LocationHistoryPoint(id = "a1", officerId = "o1", officerName = "Silva", lat = 6.92710, lng = 79.86120, timestamp = t0),
            LocationHistoryPoint(id = "a2", officerId = "o1", officerName = "Silva", lat = 6.92712, lng = 79.86122, timestamp = t0 + 10 * 60_000L),
            // Stay B (~1km away): 26 minutes (>= 20 min threshold)
            LocationHistoryPoint(id = "b1", officerId = "o1", officerName = "Silva", lat = 6.93710, lng = 79.86120, timestamp = t0 + 15 * 60_000L),
            LocationHistoryPoint(id = "b2", officerId = "o1", officerName = "Silva", lat = 6.93711, lng = 79.86121, timestamp = t0 + 41 * 60_000L)
        )

        val stays = GeoRouteUtils.extractStayLocations(points)
        assertEquals(2, stays.size)
        assertEquals(10L, stays[0].durationMinutes)
        assertTrue(stays[0].durationMinutes < GeoRouteUtils.LONG_STAY_THRESHOLD_MINUTES)

        assertEquals(26L, stays[1].durationMinutes)
        assertTrue(stays[1].durationMinutes >= GeoRouteUtils.LONG_STAY_THRESHOLD_MINUTES)
    }

    @Test
    fun resolveShiftWindowStartMs_handlesMultiDayOnDutyToOffDutyShifts() {
        val now = 1_700_500_000_000L
        val twoDaysMs = 2L * 24L * 60L * 60L * 1000L
        val threeDaysMs = 3L * 24L * 60L * 60L * 1000L

        // Active multi-day ON_DUTY shift started 2 days ago
        val activeRecords = listOf(
            DutyRecord(officerId = "o1", type = "ON_DUTY", timestamp = now - twoDaysMs)
        )
        assertEquals(now - twoDaysMs, GeoRouteUtils.resolveShiftWindowStartMs(activeRecords, now))

        // Completed multi-day shift: ON_DUTY 3 days ago -> OFF_DUTY 1 hour ago
        val completedRecords = listOf(
            DutyRecord(officerId = "o1", type = "ON_DUTY", timestamp = now - threeDaysMs),
            DutyRecord(officerId = "o1", type = "OFF_DUTY", timestamp = now - 3_600_000L)
        )
        assertEquals(now - threeDaysMs, GeoRouteUtils.resolveShiftWindowStartMs(completedRecords, now))

        // Empty falls back to 24h ago
        assertEquals(now - 24L * 60L * 60L * 1000L, GeoRouteUtils.resolveShiftWindowStartMs(emptyList(), now))
    }

    @Test
    fun stationChatMessage_formatsSenderHeadersForOicAdminAndOfficers() {
        val oicMsg = StationChatMessage(
            senderRole = "OIC",
            senderName = "K. Perera",
            senderRank = "Inspector of Police (IP)",
            senderBadge = "OIC-100"
        )
        assertEquals("ස්ථානාධිපති (OIC) • K. Perera", oicMsg.formatSenderHeader())

        val adminMsg = StationChatMessage(
            senderRole = "ADMIN_BRANCH",
            senderName = "N. Fernando",
            senderRank = "Police Sergeant (PS)"
        )
        assertEquals("පාලන අංශය (Admin) • N. Fernando", adminMsg.formatSenderHeader())

        val officerWithBadge = StationChatMessage(
            senderRole = "OFFICER",
            senderName = "D. Silva",
            senderRank = "Police Constable (PC)",
            senderBadge = "84512"
        )
        assertEquals("PC (84512) • D. Silva", officerWithBadge.formatSenderHeader())

        val officerRankOnly = StationChatMessage(
            senderRole = "OFFICER",
            senderName = "R. Jayasinghe",
            senderRank = "Sub-Inspector (SI)",
            senderBadge = ""
        )
        assertEquals("SI • R. Jayasinghe", officerRankOnly.formatSenderHeader())

        val officerBadgeOnly = StationChatMessage(
            senderRole = "OFFICER",
            senderName = "S. Kumara",
            senderRank = "",
            senderBadge = "9901"
        )
        assertEquals("No: 9901 • S. Kumara", officerBadgeOnly.formatSenderHeader())
    }

    @Test
    fun dateTimeUtils_formatsSinhalaDurationAccurately() {
        assertEquals("විනාඩි 0", DateTimeUtils.formatSinhalaDuration(0L))
        assertEquals("විනාඩි 25", DateTimeUtils.formatSinhalaDuration(25L))
        assertEquals("පැය 2 විනාඩි 5", DateTimeUtils.formatSinhalaDuration(125L))
        assertEquals("දින 1 පැය 1 විනාඩි 5", DateTimeUtils.formatSinhalaDuration(25 * 60L + 5L))
    }

    @Test
    fun chatAttachmentUtils_enforces10MbLimitRetentionAndChunkReassembly() {
        // 10 MB boundary check
        assertFalse(com.example.util.ChatAttachmentUtils.isWithinSizeLimit(0L))
        assertTrue(com.example.util.ChatAttachmentUtils.isWithinSizeLimit(10L * 1024L * 1024L))
        assertFalse(com.example.util.ChatAttachmentUtils.isWithinSizeLimit(10L * 1024L * 1024L + 1L))

        // 48h retention check
        val created = 1_700_000_000_000L
        val expires = com.example.util.ChatAttachmentUtils.computeExpiresAtMs(created)
        assertEquals(created + 2L * 24L * 60L * 60L * 1000L, expires)
        assertFalse(com.example.util.ChatAttachmentUtils.isExpired(created, expires, expires - 1000L))
        assertTrue(com.example.util.ChatAttachmentUtils.isExpired(created, expires, expires))
        assertTrue(com.example.util.ChatAttachmentUtils.isExpired(created, 0L, created + 48L * 60L * 60L * 1000L))

        // Message type inference
        assertEquals("PDF", com.example.util.ChatAttachmentUtils.inferMessageType("application/pdf", "order.pdf"))
        assertEquals("IMAGE", com.example.util.ChatAttachmentUtils.inferMessageType("image/jpeg", "photo.jpg"))
        assertEquals("AUDIO", com.example.util.ChatAttachmentUtils.inferMessageType("audio/mp4", "voice.m4a"))
        assertEquals("FILE", com.example.util.ChatAttachmentUtils.inferMessageType("application/zip", "report.zip"))

        // Chunk splitting + exact byte-for-byte reassembly
        val payload = ByteArray(1_000_000) { idx -> (idx % 251).toByte() }
        val chunks = com.example.util.ChatAttachmentUtils.splitByteArray(payload)
        assertEquals(3, chunks.size)
        val reassembled = chunks.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
        assertEquals(payload.size, reassembled.size)
        assertTrue(payload.contentEquals(reassembled))
    }

    @Test
    fun liveMediaCodecUtils_imaAdpcmRoundTripPreservesAudioShape() {
        val sampleCount = 1600
        val inputPcm = ShortArray(sampleCount) { i ->
            (kotlin.math.sin(i * 2.0 * kotlin.math.PI * 440.0 / 16000.0) * 12000.0).toInt().toShort()
        }
        val encoded = com.example.util.LiveMediaCodecUtils.encodeImaAdpcm(inputPcm, sampleCount)
        assertEquals(4 + (sampleCount + 1) / 2, encoded.size)

        val decoded = com.example.util.LiveMediaCodecUtils.decodeImaAdpcm(encoded, sampleCount)
        assertEquals(sampleCount, decoded.size)

        val maxDecoded = decoded.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue("Decoded sine peak should stay strong ($maxDecoded)", maxDecoded in 8000..16000)

        var meanAbsError = 0.0
        for (i in 0 until sampleCount) {
            meanAbsError += kotlin.math.abs(inputPcm[i].toInt() - decoded[i].toInt())
        }
        meanAbsError /= sampleCount
        assertTrue("IMA-ADPCM mean quantization error too high: $meanAbsError", meanAbsError < 1500.0)
    }

    @Test
    fun liveMediaCodecUtils_resolveMonitorCapabilityRespectsDutyAndPermissions() {
        val offDuty = com.example.util.LiveMediaCodecUtils.resolveMonitorCapability(
            isOnDuty = false,
            hasCameraPermission = true,
            hasMicPermission = true
        )
        assertFalse(offDuty.canStreamAny)
        assertFalse(offDuty.canStreamVideo)
        assertFalse(offDuty.canStreamAudio)

        val camOnly = com.example.util.LiveMediaCodecUtils.resolveMonitorCapability(
            isOnDuty = true,
            hasCameraPermission = true,
            hasMicPermission = false
        )
        assertTrue(camOnly.canStreamAny)
        assertTrue(camOnly.canStreamVideo)
        assertFalse(camOnly.canStreamAudio)

        val micOnly = com.example.util.LiveMediaCodecUtils.resolveMonitorCapability(
            isOnDuty = true,
            hasCameraPermission = false,
            hasMicPermission = true
        )
        assertTrue(micOnly.canStreamAny)
        assertFalse(micOnly.canStreamVideo)
        assertTrue(micOnly.canStreamAudio)

        val both = com.example.util.LiveMediaCodecUtils.resolveMonitorCapability(
            isOnDuty = true,
            hasCameraPermission = true,
            hasMicPermission = true
        )
        assertTrue(both.canStreamAny)
        assertTrue(both.canStreamVideo)
        assertTrue(both.canStreamAudio)

        val neither = com.example.util.LiveMediaCodecUtils.resolveMonitorCapability(
            isOnDuty = true,
            hasCameraPermission = false,
            hasMicPermission = false
        )
        assertFalse(neither.canStreamAny)
    }
}
