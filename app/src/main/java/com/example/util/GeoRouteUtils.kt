package com.example.util

import com.example.data.LocationHistoryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class StayLocation(
    val officerId: String,
    val officerName: String,
    val centerLat: Double,
    val centerLng: Double,
    val arrivalMs: Long,
    val departureMs: Long,
    val durationMinutes: Long,
    val pointCount: Int,
    val isOffDutySpot: Boolean = false
)

data class RouteResult(
    val points: List<Pair<Double, Double>>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val isFallbackDirect: Boolean
)

object GeoRouteUtils {

    private const val EARTH_RADIUS_METERS = 6371000.0

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        return distanceMeters(lat1, lng1, lat2, lng2) / 1000.0
    }

    fun formatDistance(distanceMeters: Double): String {
        return if (distanceMeters < 1000.0) {
            "${distanceMeters.roundToInt()} m"
        } else {
            String.format(Locale.US, "%.2f km", distanceMeters / 1000.0)
        }
    }

    fun extractLatLng(locMap: Map<String, Any>?): Triple<Double, Double, Long>? {
        if (locMap == null) return null
        val lat = (locMap["lat"] as? Number)?.toDouble() ?: return null
        val lng = (locMap["lng"] as? Number)?.toDouble() ?: return null
        if (lat == 0.0 && lng == 0.0) return null
        val ts = (locMap["timestamp"] as? Number)?.toLong() ?: 0L
        return Triple(lat, lng, ts)
    }

    /**
     * Groups contiguous breadcrumbs staying within [radiusMeters] into StayLocation clusters.
     */
    fun extractStayLocations(
        points: List<LocationHistoryPoint>,
        radiusMeters: Double = 55.0,
        minDurationMs: Long = 90_000L // 1.5 minutes
    ): List<StayLocation> {
        val valid = points.filter { it.lat != 0.0 || it.lng != 0.0 }.sortedBy { it.timestamp }
        if (valid.isEmpty()) return emptyList()

        val stays = mutableListOf<StayLocation>()
        var cluster = mutableListOf(valid.first())
        var sumLat = valid.first().lat
        var sumLng = valid.first().lng

        fun flushCluster(clusterPoints: List<LocationHistoryPoint>) {
            if (clusterPoints.isEmpty()) return
            val first = clusterPoints.first()
            val last = clusterPoints.last()
            val durationMs = (last.timestamp - first.timestamp).coerceAtLeast(0L)
            val hasOffDuty = clusterPoints.any { it.dutyType == "OFF_DUTY" }
            if (durationMs >= minDurationMs || hasOffDuty) {
                val avgLat = clusterPoints.map { it.lat }.average()
                val avgLng = clusterPoints.map { it.lng }.average()
                stays.add(
                    StayLocation(
                        officerId = first.officerId,
                        officerName = first.officerName,
                        centerLat = avgLat,
                        centerLng = avgLng,
                        arrivalMs = first.timestamp,
                        departureMs = last.timestamp,
                        durationMinutes = (durationMs / 60_000L).coerceAtLeast(if (hasOffDuty) 0L else 1L),
                        pointCount = clusterPoints.size,
                        isOffDutySpot = hasOffDuty
                    )
                )
            }
        }

        for (i in 1 until valid.size) {
            val pt = valid[i]
            val cLat = sumLat / cluster.size
            val cLng = sumLng / cluster.size
            val dist = distanceMeters(cLat, cLng, pt.lat, pt.lng)
            if (dist <= radiusMeters) {
                cluster.add(pt)
                sumLat += pt.lat
                sumLng += pt.lng
            } else {
                flushCluster(cluster)
                cluster = mutableListOf(pt)
                sumLat = pt.lat
                sumLng = pt.lng
            }
        }
        flushCluster(cluster)
        return stays
    }

    /**
     * Fetches driving route geometry from OSRM public router with graceful straight-line fallback.
     */
    suspend fun fetchDrivingRoute(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double
    ): RouteResult = withContext(Dispatchers.IO) {
        val straightDist = distanceMeters(fromLat, fromLng, toLat, toLng)
        if ((fromLat == 0.0 && fromLng == 0.0) || (toLat == 0.0 && toLng == 0.0)) {
            return@withContext RouteResult(
                points = listOf(Pair(toLat, toLng)),
                distanceMeters = 0.0,
                durationSeconds = 0.0,
                isFallbackDirect = true
            )
        }
        try {
            val urlString = String.format(
                Locale.US,
                "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                fromLng, fromLat, toLng, toLat
            )
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "AllInOnePoliceApp/1.0")
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val routes = json.optJSONArray("routes")
                if (routes != null && routes.length() > 0) {
                    val first = routes.getJSONObject(0)
                    val dist = first.optDouble("distance", straightDist)
                    val dur = first.optDouble("duration", (dist / 11.0))
                    val coords = first.getJSONObject("geometry").getJSONArray("coordinates")
                    val pts = ArrayList<Pair<Double, Double>>(coords.length())
                    for (i in 0 until coords.length()) {
                        val c = coords.getJSONArray(i)
                        pts.add(Pair(c.getDouble(1), c.getDouble(0))) // GeoJSON is [lng, lat]
                    }
                    if (pts.size >= 2) {
                        return@withContext RouteResult(
                            points = pts,
                            distanceMeters = dist,
                            durationSeconds = dur,
                            isFallbackDirect = false
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Fall back to direct route below
        }
        RouteResult(
            points = listOf(Pair(fromLat, fromLng), Pair(toLat, toLng)),
            distanceMeters = straightDist,
            durationSeconds = (straightDist / 11.1), // ~40 km/h estimate
            isFallbackDirect = true
        )
    }
}
