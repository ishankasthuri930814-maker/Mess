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
}
