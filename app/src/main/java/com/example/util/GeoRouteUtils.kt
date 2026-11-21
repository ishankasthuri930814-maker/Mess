package com.example.util

import com.example.data.DutyRecord
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
    const val LONG_STAY_THRESHOLD_MINUTES = 20L

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

    fun computePolylineDistanceMeters(points: List<LocationHistoryPoint>): Double {
        val valid = points.filter { it.lat != 0.0 || it.lng != 0.0 }.sortedBy { it.timestamp }
        if (valid.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until valid.size) {
            val prev = valid[i - 1]
            val curr = valid[i]
            val step = distanceMeters(prev.lat, prev.lng, curr.lat, curr.lng)
            if (step >= 1.5 && step <= 10_000.0) {
                total += step
            }
        }
        return total
    }

    /**
     * Filters GPS spikes/jitter and applies Chaikin corner smoothing so travel trails render smoothly.
     */
    fun filterAndSmoothTrail(points: List<LocationHistoryPoint>): List<Pair<Double, Double>> {
        val sorted = points.filter { it.lat != 0.0 || it.lng != 0.0 }.sortedBy { it.timestamp }
        if (sorted.size <= 2) {
            return sorted.map { Pair(it.lat, it.lng) }
        }

        // 1. De-noise impossible speed spikes & sub-2m jitter while keeping endpoints
        val clean = mutableListOf(sorted.first())
        for (i in 1 until sorted.size - 1) {
            val prev = clean.last()
            val curr = sorted[i]
            val dist = distanceMeters(prev.lat, prev.lng, curr.lat, curr.lng)
            val dtSec = ((curr.timestamp - prev.timestamp) / 1000.0).coerceAtLeast(1.0)
            val speedMps = dist / dtSec
            // Reject implausible teleport (> 65 m/s = 234 km/h) or tiny noise (< 2.0 m)
            if (dist >= 2.0 && speedMps <= 65.0) {
                clean.add(curr)
            }
        }
        val last = sorted.last()
        if (clean.last().lat != last.lat || clean.last().lng != last.lng) {
            val tailDist = distanceMeters(clean.last().lat, clean.last().lng, last.lat, last.lng)
            val tailDtSec = ((last.timestamp - clean.last().timestamp) / 1000.0).coerceAtLeast(1.0)
            if (tailDist / tailDtSec <= 65.0) {
                clean.add(last)
            }
        }

        var coords = clean.map { Pair(it.lat, it.lng) }
        if (coords.size < 3) return coords

        // 2. Apply 2 passes of Chaikin corner-cutting for smooth road curves (break if huge segment jump > 250m)
        repeat(2) {
            val smoothed = ArrayList<Pair<Double, Double>>(coords.size * 2)
            smoothed.add(coords.first())
            for (i in 0 until coords.size - 1) {
                val p0 = coords[i]
                val p1 = coords[i + 1]
                val segDist = distanceMeters(p0.first, p0.second, p1.first, p1.second)
                if (segDist <= 250.0) {
                    val q = Pair(0.75 * p0.first + 0.25 * p1.first, 0.75 * p0.second + 0.25 * p1.second)
                    val r = Pair(0.25 * p0.first + 0.75 * p1.first, 0.25 * p0.second + 0.75 * p1.second)
                    smoothed.add(q)
                    smoothed.add(r)
                } else {
                    smoothed.add(p0)
                    smoothed.add(p1)
                }
            }
            smoothed.add(coords.last())
            coords = smoothed
        }
        return coords
    }

    /**
     * Resolves the timestamp window start for an officer's continuous ON_DUTY -> OFF_DUTY shift
     * (supports multi-day shifts spanning consecutive calendar days).
     */
    fun resolveShiftWindowStartMs(
        dutyRecords: List<DutyRecord>,
        nowMs: Long = System.currentTimeMillis(),
        maxLookbackDays: Int = 14
    ): Long {
        val oldestAllowed = nowMs - maxLookbackDays * 24L * 60L * 60L * 1000L
        val shiftRecords = dutyRecords
            .filter { it.type == "ON_DUTY" || it.type == "OFF_DUTY" }
            .sortedByDescending { it.timestamp }
        val latestShift = shiftRecords.firstOrNull()
        val startMs = when {
            latestShift == null -> nowMs - 24L * 60L * 60L * 1000L
            latestShift.type == "ON_DUTY" -> latestShift.timestamp
            else -> {
                // Find the matching ON_DUTY immediately preceding this OFF_DUTY
                val matchingOn = shiftRecords.firstOrNull {
                    it.type == "ON_DUTY" && it.timestamp <= latestShift.timestamp
                }
                matchingOn?.timestamp ?: (nowMs - 24L * 60L * 60L * 1000L)
            }
        }
        return startMs.coerceAtLeast(oldestAllowed)
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
