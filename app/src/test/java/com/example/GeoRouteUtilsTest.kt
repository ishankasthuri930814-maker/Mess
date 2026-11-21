package com.example

import com.example.data.LocationHistoryPoint
import com.example.util.GeoRouteUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoRouteUtilsTest {

    @Test
    fun distanceMeters_calculatesAccurateColomboToKandyDistance() {
        // Colombo Fort (~6.9344, 79.8428) to Kandy (~7.2906, 80.6337) ~ 95 km straight line
        val meters = GeoRouteUtils.distanceMeters(6.9344, 79.8428, 7.2906, 80.6337)
        val km = meters / 1000.0
        assertTrue("Expected ~95km but got $km", km in 90.0..102.0)
    }

    @Test
    fun extractLatLng_handlesValidAndInvalidMaps() {
        assertNull(GeoRouteUtils.extractLatLng(null))
        assertNull(GeoRouteUtils.extractLatLng(mapOf("lat" to 0.0, "lng" to 0.0)))

        val valid = GeoRouteUtils.extractLatLng(
            mapOf("lat" to 6.9271, "lng" to 79.8612, "timestamp" to 1700000000000L)
        )
        assertNotNull(valid)
        assertEquals(6.9271, valid!!.first, 0.0001)
        assertEquals(79.8612, valid.second, 0.0001)
        assertEquals(1700000000000L, valid.third)
    }

    @Test
    fun extractStayLocations_groupsDwellClustersAndOffDutySpots() {
        val t0 = 1_700_000_000_000L
        val points = listOf(
            // Cluster 1: staying ~3 mins at Point A
            LocationHistoryPoint(
                id = "1",
                officerId = "off1",
                officerName = "PC Silva",
                lat = 6.92710,
                lng = 79.86120,
                dutyType = "ON_DUTY",
                timestamp = t0
            ),
            LocationHistoryPoint(
                id = "2",
                officerId = "off1",
                officerName = "PC Silva",
                lat = 6.92718,
                lng = 79.86125,
                dutyType = "ON_DUTY",
                timestamp = t0 + 180_000L
            ),
            // Quick transit point ~500m away (should NOT form a stay)
            LocationHistoryPoint(
                id = "3",
                officerId = "off1",
                officerName = "PC Silva",
                lat = 6.93200,
                lng = 79.86500,
                dutyType = "ON_DUTY",
                timestamp = t0 + 210_000L
            ),
            // Final Off-Duty point ~1km away (MUST be captured as stay / off-duty spot even if single point)
            LocationHistoryPoint(
                id = "4",
                officerId = "off1",
                officerName = "PC Silva",
                lat = 6.94100,
                lng = 79.87200,
                dutyType = "OFF_DUTY",
                timestamp = t0 + 300_000L
            )
        )

        val stays = GeoRouteUtils.extractStayLocations(points)
        assertEquals(2, stays.size)

        assertFalse(stays[0].isOffDutySpot)
        assertEquals(3L, stays[0].durationMinutes)
        assertEquals(2, stays[0].pointCount)

        assertTrue(stays[1].isOffDutySpot)
        assertEquals(6.94100, stays[1].centerLat, 0.0001)
    }

    @Test
    fun computePolylineDistanceMeters_tracksTinyWalksAndFiltersNoise() {
        val t0 = 1_700_000_000_000L
        // Step 1: ~0.000045 deg lat ~= 5.0 meters
        // Step 2: tiny 0.5m GPS jitter (ignored because < 1.5m)
        // Step 3: ~0.000090 deg lat ~= 10.0 meters
        val points = listOf(
            LocationHistoryPoint(id = "p1", lat = 6.927100, lng = 79.861200, timestamp = t0),
            LocationHistoryPoint(id = "p2", lat = 6.927145, lng = 79.861200, timestamp = t0 + 3_000L),
            LocationHistoryPoint(id = "p3", lat = 6.927149, lng = 79.861200, timestamp = t0 + 6_000L),
            LocationHistoryPoint(id = "p4", lat = 6.927239, lng = 79.861200, timestamp = t0 + 9_000L)
        )

        val totalMeters = GeoRouteUtils.computePolylineDistanceMeters(points)
        assertTrue("Expected ~15.0m for short walk, got $totalMeters", totalMeters in 13.0..17.5)
        assertEquals(0.0, GeoRouteUtils.computePolylineDistanceMeters(points.take(1)), 0.01)
    }

    @Test
    fun computePolylineDistanceMeters_returnsZeroForStationaryGpsWobbleAndSpikes() {
        val t0 = 1_700_000_000_000L
        // Stationary officer standing at junction with ±8m..±14m GPS wobble and a 1.5km cell jump + coarse 90m outlier
        val stationaryPoints = listOf(
            LocationHistoryPoint(id = "s1", officerId = "o1", lat = 6.927100, lng = 79.861200, accuracy = 8f, timestamp = t0),
            // 5m wobble inside uncertainty circle
            LocationHistoryPoint(id = "s2", officerId = "o1", lat = 6.927145, lng = 79.861210, accuracy = 12f, timestamp = t0 + 10_000L),
            // Coarse Wi-Fi/Cell fix (accuracy 95m) 1.5 km away
            LocationHistoryPoint(id = "outlier", officerId = "o1", lat = 6.940000, lng = 79.870000, accuracy = 95f, timestamp = t0 + 20_000L),
            // Returns to same stationary junction with 10m wobble
            LocationHistoryPoint(id = "s3", officerId = "o1", lat = 6.927120, lng = 79.861225, accuracy = 10f, timestamp = t0 + 30_000L),
            // Isolated out-and-back multipath bounce 250m away even with nominal 15m accuracy
            LocationHistoryPoint(id = "bounce", officerId = "o1", lat = 6.929350, lng = 79.861200, accuracy = 15f, timestamp = t0 + 80_000L),
            // Anchored 25-min stay heartbeat at same spot
            LocationHistoryPoint(id = "s4", officerId = "o1", lat = 6.927105, lng = 79.861205, accuracy = 6f, timestamp = t0 + 25 * 60_000L)
        )

        val dist = GeoRouteUtils.computePolylineDistanceMeters(stationaryPoints)
        assertEquals("Stationary officer must report 0m travel distance", 0.0, dist, 0.01)

        val stays = GeoRouteUtils.extractStayLocations(stationaryPoints)
        assertEquals(1, stays.size)
        assertEquals(25L, stays.first().durationMinutes)
    }
}
