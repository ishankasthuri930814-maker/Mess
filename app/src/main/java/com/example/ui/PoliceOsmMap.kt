package com.example.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.AppUser
import com.example.data.EmergencyAlert
import com.example.data.LocationHistoryPoint
import com.example.ui.theme.BadgeGoldBright
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.DutyOffOrange
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import com.example.ui.theme.PoliceThemeColors
import com.example.ui.theme.isDark
import com.example.util.DateTimeUtils
import com.example.util.GeoRouteUtils
import com.example.util.StayLocation
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Calendar
import java.util.Locale

enum class PoliceMapStyle(val label: String) {
    STREET("වීදි සිතියම"),
    CLEAR("පැහැදිලි"),
    SATELLITE("චන්ද්‍රිකා")
}

enum class MapOfficerFilter(val label: String) {
    ALL("සියල්ල"),
    ON_DUTY("ON DUTY"),
    OFF_DUTY("OFF DUTY")
}

private sealed class SelectedMapOverlay {
    data class OfficerPin(
        val officer: AppUser,
        val lat: Double,
        val lng: Double,
        val timestamp: Long,
        val stayCount: Int
    ) : SelectedMapOverlay()

    data class StaySpotPin(
        val stay: StayLocation
    ) : SelectedMapOverlay()

    data class EmergencyPin(
        val alert: EmergencyAlert
    ) : SelectedMapOverlay()
}

private fun createOsmHumanitarianTileSource(): OnlineTileSourceBase {
    return XYTileSource(
        "OSM_Humanitarian_SL",
        0, 19, 256, ".png",
        arrayOf(
            "https://a.tile.openstreetmap.fr/hot/",
            "https://b.tile.openstreetmap.fr/hot/",
            "https://c.tile.openstreetmap.fr/hot/"
        ),
        "© OpenStreetMap contributors, HOT"
    )
}

private fun createOsmStandardTileSource(): OnlineTileSourceBase {
    return XYTileSource(
        "OSM_Standard_SL",
        0, 19, 256, ".png",
        arrayOf("https://tile.openstreetmap.org/"),
        "© OpenStreetMap contributors"
    )
}

private fun createEsriSatelliteTileSource(): OnlineTileSourceBase {
    return object : OnlineTileSourceBase(
        "EsriWorldImagery_SL",
        0, 16, 256, ".jpg",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            return "${baseUrl}$z/$y/$x"
        }
    }
}

@Composable
fun PoliceLiveMapView(
    officers: List<AppUser>,
    locationHistory: List<LocationHistoryPoint>,
    emergencyAlerts: List<EmergencyAlert>,
    myLocation: Pair<Double, Double>?,
    myLocationAccuracyMeters: Float? = null,
    activeRouteState: ActiveRouteState?,
    selectedDateKey: String,
    isOicView: Boolean,
    onSelectDate: (String) -> Unit,
    onNavigateToTarget: (MapNavigationTarget) -> Unit,
    onClearRoute: () -> Unit,
    onResolveEmergency: (String) -> Unit,
    onRefreshGps: () -> Unit,
    onOpenEmergencySender: (() -> Unit)? = null,
    onExpandFullscreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var mapStyle by remember { mutableStateOf(PoliceMapStyle.STREET) }
    var filterMode by remember { mutableStateOf(MapOfficerFilter.ALL) }
    var focusedOfficerUid by remember { mutableStateOf<String?>(null) }
    var showTrailsAndStays by remember { mutableStateOf(true) }
    var followMyLocation by remember { mutableStateOf(false) }
    var selectedOverlay by remember { mutableStateOf<SelectedMapOverlay?>(null) }

    val osmStandardSource = remember { createOsmStandardTileSource() }
    val osmClearSource = remember { createOsmHumanitarianTileSource() }
    val esriSatelliteSource = remember { createEsriSatelliteTileSource() }

    // Configure osmdroid cleanly inside app cacheDir with compliant User-Agent and clean v3 tile cache
    remember(context) {
        val osmdroidPrefs = context.applicationContext.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        Configuration.getInstance().load(context.applicationContext, osmdroidPrefs)
        Configuration.getInstance().userAgentValue = "AllInOnePolice-SL/1.0 (${context.packageName})"
        val baseDir = File(context.cacheDir, "osmdroid")
        listOf("tiles", "tiles_v2").forEach { legacy ->
            val legacyDir = File(baseDir, legacy)
            if (legacyDir.exists()) legacyDir.deleteRecursively()
        }
        val tileCacheDir = File(baseDir, "tiles_v3")
        if (!tileCacheDir.exists()) tileCacheDir.mkdirs()
        Configuration.getInstance().osmdroidBasePath = baseDir
        Configuration.getInstance().osmdroidTileCache = tileCacheDir
        true
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(osmStandardSource)
            setMultiTouchControls(true)
            setTilesScaledToDpi(true)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            overlayManager.tilesOverlay.loadingBackgroundColor = android.graphics.Color.parseColor("#E2E8F0")
            overlayManager.tilesOverlay.loadingLineColor = android.graphics.Color.parseColor("#CBD5E1")
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 6.0
            maxZoomLevel = 19.0
            controller.setZoom(15.5)
            controller.setCenter(GeoPoint(7.8731, 80.7718))
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN,
                    android.view.MotionEvent.ACTION_MOVE -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        if (event.actionMasked == android.view.MotionEvent.ACTION_MOVE) {
                            followMyLocation = false
                        }
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }
        }
    }

    var hasDoneInitialFit by remember { mutableStateOf(false) }
    var hasCenteredOnMyLocation by remember { mutableStateOf(false) }
    var lastFocusedRouteId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // Filter visible officers
    val approvedOfficers = remember(officers) {
        officers.filter { it.status == "APPROVED" }
    }
    val visibleOfficers = remember(approvedOfficers, filterMode, focusedOfficerUid) {
        if (focusedOfficerUid != null) {
            approvedOfficers.filter { it.uid == focusedOfficerUid }
        } else {
            when (filterMode) {
                MapOfficerFilter.ALL -> approvedOfficers
                MapOfficerFilter.ON_DUTY -> approvedOfficers.filter { it.isOnDuty }
                MapOfficerFilter.OFF_DUTY -> approvedOfficers.filter { !it.isOnDuty }
            }
        }
    }
    val visibleOfficerIds = remember(visibleOfficers) { visibleOfficers.map { it.uid }.toSet() }

    // Precompute trails & stay locations grouped by officer
    val groupedHistory = remember(locationHistory, visibleOfficerIds, isOicView, approvedOfficers.size) {
        val filtered = if ((isOicView || approvedOfficers.size > 1) && visibleOfficerIds.isNotEmpty()) {
            locationHistory.filter { it.officerId in visibleOfficerIds }
        } else {
            locationHistory
        }
        filtered.groupBy { it.officerId }
    }

    val staysByOfficer = remember(groupedHistory) {
        groupedHistory.mapValues { (_, pts) -> GeoRouteUtils.extractStayLocations(pts) }
    }

    fun openExternalNavigation(lat: Double, lng: Double, label: String) {
        try {
            val navIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng"))
            context.startActivity(navIntent)
        } catch (_: Exception) {
            try {
                val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})"))
                context.startActivity(geoIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "බාහිර Navigation App එකක් විවෘත කළ නොහැක", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun dialPhone(phone: String) {
        val clean = phone.replace(" ", "").replace("-", "")
        if (clean.isBlank()) return
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")))
        } catch (_: Exception) {
            Toast.makeText(context, "ඇමතුම් ලබාගත නොහැක", Toast.LENGTH_SHORT).show()
        }
    }

    fun fitAllVisiblePoints() {
        val pts = mutableListOf<GeoPoint>()
        activeRouteState?.let { route ->
            if (route.originLat != 0.0) pts.add(GeoPoint(route.originLat, route.originLng))
            pts.add(GeoPoint(route.target.lat, route.target.lng))
            route.routePoints.forEach { pts.add(GeoPoint(it.first, it.second)) }
        }
        visibleOfficers.forEach { officer ->
            val locTriple = if (officer.isOnDuty) {
                GeoRouteUtils.extractLatLng(officer.lastLocation)
            } else {
                GeoRouteUtils.extractLatLng(officer.lastOffDutyLocation) ?: GeoRouteUtils.extractLatLng(officer.lastLocation)
            }
            if (locTriple != null) pts.add(GeoPoint(locTriple.first, locTriple.second))
        }
        groupedHistory.values.flatten().forEach {
            if (it.lat != 0.0 || it.lng != 0.0) pts.add(GeoPoint(it.lat, it.lng))
        }
        emergencyAlerts.forEach {
            if (it.lat != 0.0 || it.lng != 0.0) pts.add(GeoPoint(it.lat, it.lng))
        }
        myLocation?.let { pts.add(GeoPoint(it.first, it.second)) }

        if (pts.size == 1) {
            mapView.controller.animateTo(pts.first(), 17.8, 450L)
        } else if (pts.size > 1) {
            try {
                val box = BoundingBox.fromGeoPointsSafe(pts)
                if (box.diagonalLengthInMeters < 35.0) {
                    mapView.controller.animateTo(box.centerWithDateLine, 18.2, 450L)
                } else {
                    mapView.zoomToBoundingBox(box, true, 100, 18.2, 450L)
                }
            } catch (_: Exception) {
                mapView.controller.animateTo(pts.first(), 17.2, 450L)
            }
        }
    }

    fun openMapDatePicker() {
        val parts = selectedDateKey.split("-")
        val cal = Calendar.getInstance()
        val year = parts.getOrNull(0)?.toIntOrNull() ?: cal.get(Calendar.YEAR)
        val month = (parts.getOrNull(1)?.toIntOrNull()?.minus(1)) ?: cal.get(Calendar.MONTH)
        val day = parts.getOrNull(2)?.toIntOrNull() ?: cal.get(Calendar.DAY_OF_MONTH)
        DatePickerDialog(
            context,
            { _, y, m, d ->
                onSelectDate(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
            },
            year, month, day
        ).show()
    }

    // Auto-zoom when a new navigation route is activated
    LaunchedEffect(activeRouteState) {
        val route = activeRouteState
        if (route != null && !route.isLoading) {
            val routeKey = "${route.target.alertId}_${route.target.lat}_${route.target.lng}_${route.routePoints.size}"
            if (routeKey != lastFocusedRouteId) {
                lastFocusedRouteId = routeKey
                fitAllVisiblePoints()
            }
        }
    }

    // Auto-zoom when selecting an individual officer
    LaunchedEffect(focusedOfficerUid) {
        if (focusedOfficerUid != null) {
            fitAllVisiblePoints()
        }
    }

    // When first live GPS location arrives, center/fit if not manually browsing a specific officer/route
    LaunchedEffect(myLocation) {
        if (myLocation != null && !hasCenteredOnMyLocation && activeRouteState == null && focusedOfficerUid == null) {
            hasCenteredOnMyLocation = true
            fitAllVisiblePoints()
        }
    }

    // Auto-pan smoothly to follow live GPS dot when Follow Mode is enabled
    LaunchedEffect(myLocation, followMyLocation) {
        if (followMyLocation && myLocation != null) {
            val targetZoom = maxOf(mapView.zoomLevelDouble, 18.0)
            mapView.controller.animateTo(GeoPoint(myLocation.first, myLocation.second), targetZoom, 350L)
        }
    }

    val totalVisibleTrailMeters = remember(groupedHistory) {
        groupedHistory.values.sumOf { GeoRouteUtils.computePolylineDistanceMeters(it) }
    }

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .testTag("police_osm_map"),
            update = { view ->
                val targetSource = when (mapStyle) {
                    PoliceMapStyle.STREET -> osmStandardSource
                    PoliceMapStyle.CLEAR -> osmClearSource
                    PoliceMapStyle.SATELLITE -> esriSatelliteSource
                }
                if (view.tileProvider.tileSource.name() != targetSource.name()) {
                    view.setTileSource(targetSource)
                }
                view.maxZoomLevel = if (mapStyle == PoliceMapStyle.SATELLITE) 17.5 else 19.0

                view.overlays.clear()

                // 1. Travel Paths (Polylines) & Stay Locations (Dwell circles + markers)
                if (showTrailsAndStays) {
                    groupedHistory.forEach { (officerId, pts) ->
                        val sortedPts = pts.filter { it.lat != 0.0 || it.lng != 0.0 }.sortedBy { it.timestamp }
                        val trailColor = MapMarkerBitmapHelper.getTrailColorForOfficer(officerId)

                        if (sortedPts.size >= 2) {
                            val polyline = Polyline(view).apply {
                                setPoints(sortedPts.map { GeoPoint(it.lat, it.lng) })
                                outlinePaint.color = trailColor
                                outlinePaint.strokeWidth = if (focusedOfficerUid == officerId) 11f else 7f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.strokeJoin = Paint.Join.ROUND
                                title = "${sortedPts.first().officerName.ifBlank { "Officer" }} - ගමන් මාර්ගය"
                            }
                            view.overlays.add(polyline)
                        }

                        // Stay / Dwell points
                        val stays = staysByOfficer[officerId].orEmpty()
                        stays.forEachIndexed { idx, stay ->
                            val zoneCircle = Polygon(view).apply {
                                points = Polygon.pointsAsCircle(GeoPoint(stay.centerLat, stay.centerLng), 48.0)
                                fillPaint.color = android.graphics.Color.argb(40, 109, 40, 217)
                                outlinePaint.color = android.graphics.Color.argb(170, 109, 40, 217)
                                outlinePaint.strokeWidth = 2.5f
                            }
                            view.overlays.add(zoneCircle)

                            val stayLabel = if (stay.isOffDutySpot) "OFF" else "${stay.durationMinutes}m"
                            val stayMarker = Marker(view).apply {
                                position = GeoPoint(stay.centerLat, stay.centerLng)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = MapMarkerBitmapHelper.createStayPointDrawable(context, stayLabel)
                                title = "රැඳී සිටි ස්ථානය #${idx + 1}"
                                setOnMarkerClickListener { _, _ ->
                                    selectedOverlay = SelectedMapOverlay.StaySpotPin(stay)
                                    true
                                }
                            }
                            view.overlays.add(stayMarker)
                        }
                    }
                }

                // 2. Active Navigation Route Polyline
                if (activeRouteState != null && activeRouteState.routePoints.size >= 2) {
                    val routeOutline = Polyline(view).apply {
                        setPoints(activeRouteState.routePoints.map { GeoPoint(it.first, it.second) })
                        outlinePaint.color = android.graphics.Color.parseColor("#1E3A8A")
                        outlinePaint.strokeWidth = 16f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                    }
                    val routeLine = Polyline(view).apply {
                        setPoints(activeRouteState.routePoints.map { GeoPoint(it.first, it.second) })
                        outlinePaint.color = android.graphics.Color.parseColor("#0EA5E9")
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                    }
                    view.overlays.add(routeOutline)
                    view.overlays.add(routeLine)
                }

                // 3. Emergency SOS Radius & Pins
                emergencyAlerts.forEach { alert ->
                    if (alert.lat != 0.0 || alert.lng != 0.0) {
                        val sosCircle = Polygon(view).apply {
                            points = Polygon.pointsAsCircle(GeoPoint(alert.lat, alert.lng), 250.0)
                            fillPaint.color = android.graphics.Color.argb(45, 220, 38, 38)
                            outlinePaint.color = android.graphics.Color.argb(180, 220, 38, 38)
                            outlinePaint.strokeWidth = 3f
                        }
                        view.overlays.add(sosCircle)

                        val sosMarker = Marker(view).apply {
                            position = GeoPoint(alert.lat, alert.lng)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = MapMarkerBitmapHelper.createEmergencyPinDrawable(context, "SOS")
                            title = "🚨 ${alert.senderName}"
                            setOnMarkerClickListener { _, _ ->
                                selectedOverlay = SelectedMapOverlay.EmergencyPin(alert)
                                onNavigateToTarget(
                                    MapNavigationTarget(
                                        alertId = alert.id,
                                        title = "🚨 හදිසි SOS: ${alert.senderName}",
                                        subtitle = alert.message,
                                        lat = alert.lat,
                                        lng = alert.lng,
                                        phone = alert.senderPhone
                                    )
                                )
                                true
                            }
                        }
                        view.overlays.add(sosMarker)
                    }
                }

                // 4. Route Target pin (if target isn't covered by an SOS marker or is an officer target)
                if (activeRouteState != null && activeRouteState.target.alertId.isBlank()) {
                    val tMarker = Marker(view).apply {
                        position = GeoPoint(activeRouteState.target.lat, activeRouteState.target.lng)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = MapMarkerBitmapHelper.createEmergencyPinDrawable(context, "GO")
                        title = activeRouteState.target.title
                    }
                    view.overlays.add(tMarker)
                }

                // 5. Officer Markers (ON DUTY + OFF DUTY Last Location)
                visibleOfficers.forEach { officer ->
                    val locTriple = if (officer.isOnDuty) {
                        GeoRouteUtils.extractLatLng(officer.lastLocation)
                    } else {
                        GeoRouteUtils.extractLatLng(officer.lastOffDutyLocation)
                            ?: GeoRouteUtils.extractLatLng(officer.lastLocation)
                    }
                    if (locTriple != null) {
                        val (lat, lng, ts) = locTriple
                        val stayCount = staysByOfficer[officer.uid]?.size ?: 0
                        val marker = Marker(view).apply {
                            position = GeoPoint(lat, lng)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = MapMarkerBitmapHelper.createOfficerPinDrawable(
                                context = context,
                                label = officer.displayName.ifBlank { officer.badgeNumber.ifBlank { "Officer" } },
                                isOnDuty = officer.isOnDuty,
                                isSelected = focusedOfficerUid == officer.uid
                            )
                            title = officer.displayName
                            setOnMarkerClickListener { _, _ ->
                                selectedOverlay = SelectedMapOverlay.OfficerPin(
                                    officer = officer,
                                    lat = lat,
                                    lng = lng,
                                    timestamp = ts,
                                    stayCount = stayCount
                                )
                                true
                            }
                        }
                        view.overlays.add(marker)
                    }
                }

                // 6. My Own Live GPS Dot + Accuracy Halo
                myLocation?.let { (lat, lng) ->
                    val accMeters = (myLocationAccuracyMeters ?: 12f).coerceIn(4f, 85f).toDouble()
                    val accuracyCircle = Polygon(view).apply {
                        points = Polygon.pointsAsCircle(GeoPoint(lat, lng), accMeters)
                        fillPaint.color = android.graphics.Color.argb(38, 37, 99, 235)
                        outlinePaint.color = android.graphics.Color.argb(140, 37, 99, 235)
                        outlinePaint.strokeWidth = 2f
                    }
                    view.overlays.add(accuracyCircle)

                    val selfMarker = Marker(view).apply {
                        position = GeoPoint(lat, lng)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = MapMarkerBitmapHelper.createMyLocationDotDrawable(context)
                        title = "ඔබ සිටින ස්ථානය (GPS ±${myLocationAccuracyMeters?.toInt() ?: 0}m)"
                    }
                    view.overlays.add(selfMarker)
                }

                view.invalidate()

                if (!hasDoneInitialFit && (visibleOfficers.isNotEmpty() || emergencyAlerts.isNotEmpty() || myLocation != null || activeRouteState != null)) {
                    hasDoneInitialFit = true
                    view.post { fitAllVisiblePoints() }
                }
            }
        )

        // --- TOP OVERLAY CONTROLS ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Map Style + Date + Trails Toggle + Fullscreen Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PoliceMapStyle.entries.forEach { style ->
                    val active = mapStyle == style
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (active) PoliceNavyPrimary else Color.White.copy(alpha = 0.94f),
                        shadowElevation = 3.dp,
                        modifier = Modifier.clickable { mapStyle = style }
                    ) {
                        Text(
                            text = style.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) Color.White else PoliceNavyDark,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Date Selector for Trails & Stay Spots
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BadgeGoldBright,
                    shadowElevation = 3.dp,
                    modifier = Modifier.clickable { openMapDatePicker() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp), tint = PoliceNavyDark)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (selectedDateKey == DateTimeUtils.getTodayKey()) "අද මාර්ග" else selectedDateKey,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PoliceNavyDark
                        )
                    }
                }

                // Trails & Stay Spots Toggle
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (showTrailsAndStays) Color(0xFF6D28D9) else Color.White.copy(alpha = 0.94f),
                    shadowElevation = 3.dp,
                    modifier = Modifier.clickable { showTrailsAndStays = !showTrailsAndStays }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Timeline,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (showTrailsAndStays) Color.White else PoliceNavyDark
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ගමන් මාර්ග/නැවතුම්",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (showTrailsAndStays) Color.White else PoliceNavyDark
                        )
                    }
                }
            }

            // Officer Filter & Individual Selector Row (OIC view or active Team Duty)
            if ((isOicView || approvedOfficers.size > 1) && approvedOfficers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MapOfficerFilter.entries.forEach { mode ->
                        val count = when (mode) {
                            MapOfficerFilter.ALL -> approvedOfficers.size
                            MapOfficerFilter.ON_DUTY -> approvedOfficers.count { it.isOnDuty }
                            MapOfficerFilter.OFF_DUTY -> approvedOfficers.count { !it.isOnDuty }
                        }
                        val active = focusedOfficerUid == null && filterMode == mode
                        val chipColor = when {
                            !active -> Color.White.copy(alpha = 0.92f)
                            mode == MapOfficerFilter.ON_DUTY -> DutyActiveGreen
                            mode == MapOfficerFilter.OFF_DUTY -> DutyOffOrange
                            else -> PoliceNavyDark
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = chipColor,
                            shadowElevation = 3.dp,
                            modifier = Modifier.clickable {
                                focusedOfficerUid = null
                                filterMode = mode
                            }
                        ) {
                            Text(
                                text = "${mode.label} ($count)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color.White else PoliceNavyDark,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Individual Officer Selection Pills
                    approvedOfficers.forEach { officer ->
                        val active = focusedOfficerUid == officer.uid
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (active) Color(0xFF0284C7) else Color.White.copy(alpha = 0.92f),
                            shadowElevation = 3.dp,
                            modifier = Modifier.clickable {
                                focusedOfficerUid = if (active) null else officer.uid
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (officer.isOnDuty) DutyActiveGreen else DutyOffOrange)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = officer.displayName.ifBlank { officer.badgeNumber },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (active) Color.White else PoliceNavyDark,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            // Live GPS accuracy + walked trail distance status pill (tappable to refresh GPS)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.86f),
                shadowElevation = 3.dp,
                modifier = Modifier.clickable { onRefreshGps() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val accText = when {
                        myLocation == null -> "GPS සොයමින්..."
                        myLocationAccuracyMeters != null -> "GPS ±${myLocationAccuracyMeters.toInt()}m"
                        else -> "GPS සක්‍රියයි"
                    }
                    Text(
                        text = accText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (myLocation != null) Color(0xFF4ADE80) else Color(0xFFFACC15)
                    )
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "ගමන් දුර: ${GeoRouteUtils.formatDistance(totalVisibleTrailMeters)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        // --- RIGHT SIDE FLOATING MAP ACTION BUTTONS ---
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (onOpenEmergencySender != null) {
                SmallFloatingActionButton(
                    onClick = onOpenEmergencySender,
                    containerColor = Color(0xFFDC2626),
                    contentColor = Color.White,
                    modifier = Modifier.testTag("map_sos_btn")
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Send Emergency SOS")
                }
            }
            SmallFloatingActionButton(
                onClick = {
                    followMyLocation = false
                    fitAllVisiblePoints()
                },
                containerColor = Color.White,
                contentColor = PoliceNavyDark
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Fit All Officers")
            }
            SmallFloatingActionButton(
                onClick = {
                    followMyLocation = !followMyLocation
                    onRefreshGps()
                    myLocation?.let { (lat, lng) ->
                        mapView.controller.animateTo(GeoPoint(lat, lng), 18.8, 400L)
                    }
                },
                containerColor = if (followMyLocation) PoliceNavyPrimary else Color.White,
                contentColor = if (followMyLocation) Color.White else PoliceNavyPrimary
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My GPS Location")
            }
            SmallFloatingActionButton(
                onClick = { mapView.controller.zoomIn() },
                containerColor = Color.White,
                contentColor = PoliceNavyDark
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }
            SmallFloatingActionButton(
                onClick = { mapView.controller.zoomOut() },
                containerColor = Color.White,
                contentColor = PoliceNavyDark
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }
            if (onExpandFullscreen != null) {
                SmallFloatingActionButton(
                    onClick = onExpandFullscreen,
                    containerColor = PoliceNavyPrimary,
                    contentColor = BadgeGoldBright
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen Map")
                }
            }
        }

        // --- BOTTOM OVERLAY: ACTIVE ROUTE CARD OR SELECTED MARKER DETAIL CARD ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Active Navigation Route Card
            if (activeRouteState != null) {
                val route = activeRouteState
                val distText = GeoRouteUtils.formatDistance(route.distanceMeters)
                val mins = ((route.durationSeconds / 60.0).toInt()).coerceAtLeast(1)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_route_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Directions,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = route.target.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (route.target.subtitle.isNotBlank()) {
                                    Text(
                                        text = route.target.subtitle,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = onClearRoute) {
                                Icon(Icons.Default.Close, contentDescription = "Close Route", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (route.isLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF38BDF8), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("මාර්ගය සොයමින් පවතී...", color = Color(0xFF38BDF8), fontSize = 12.sp)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0284C7).copy(alpha = 0.25f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (route.originLat == 0.0) {
                                            "ඉලක්කය: ${String.format("%.4f", route.target.lat)}, ${String.format("%.4f", route.target.lng)}"
                                        } else {
                                            "දුර: $distText • ආසන්න කාලය: මිනි. $mins${if (route.isFallbackDirect) " (සෘජු)" else ""}"
                                        },
                                        color = Color(0xFF7DD3FC),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        openExternalNavigation(route.target.lat, route.target.lng, route.target.title)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0284C7),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Turn-by-Turn GPS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                if (route.target.phone.isNotBlank()) {
                                    Button(
                                        onClick = { dialPhone(route.target.phone) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = DutyActiveGreen,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Call", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (route.target.alertId.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { onResolveEmergency(route.target.alertId) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BadgeGoldBright),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("අවසන්", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (selectedOverlay != null) {
                when (val sel = selectedOverlay) {
                    is SelectedMapOverlay.OfficerPin -> {
                        val officer = sel.officer
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(if (officer.isOnDuty) DutyActiveGreen else DutyOffOrange)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${officer.displayName} (${if (officer.isOnDuty) "ON DUTY" else "OFF DUTY අවසන් ස්ථානය"})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "${officer.rank.ifBlank { "Officer" }} • ${officer.badgeNumber.ifBlank { "N/A" }} • නැවතුම් ${sel.stayCount} ක්",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (sel.timestamp > 0L) {
                                            Text(
                                                text = "අවසන් GPS වේලාව: ${DateTimeUtils.formatFullDateTime(sel.timestamp)}",
                                                fontSize = 10.sp,
                                                color = PoliceThemeColors.navyText
                                            )
                                        }
                                    }
                                    IconButton(onClick = { selectedOverlay = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close")
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            onNavigateToTarget(
                                                MapNavigationTarget(
                                                    title = officer.displayName,
                                                    subtitle = if (officer.isOnDuty) "සජීවී රාජකාරි ස්ථානය" else "අවසන් OFF-DUTY ස්ථානය",
                                                    lat = sel.lat,
                                                    lng = sel.lng,
                                                    phone = officer.phoneNumber
                                                )
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PoliceNavyPrimary,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("මාර්ගය පෙන්වන්න", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (isOicView) {
                                        OutlinedButton(
                                            onClick = {
                                                focusedOfficerUid = if (focusedOfficerUid == officer.uid) null else officer.uid
                                                showTrailsAndStays = true
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (focusedOfficerUid == officer.uid) "සියල්ල" else "ගමන් මග",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    if (officer.phoneNumber.isNotBlank()) {
                                        IconButton(
                                            onClick = { dialPhone(officer.phoneNumber) },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(PoliceThemeColors.greenText.copy(alpha = 0.15f), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Call, contentDescription = "Call", tint = PoliceThemeColors.greenText)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is SelectedMapOverlay.StaySpotPin -> {
                        val stay = sel.stay
                        val stayAccentColor = if (MaterialTheme.colorScheme.isDark) Color(0xFFC4B5FD) else Color(0xFF6D28D9)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = stayAccentColor, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${stay.officerName.ifBlank { "නිලධාරියා" }} - ${if (stay.isOffDutySpot) "අවසන් OFF-DUTY ස්ථානය" else "රැඳී සිටි ස්ථානය"}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "කාලය: ${DateTimeUtils.formatTime(stay.arrivalMs)} - ${DateTimeUtils.formatTime(stay.departureMs)} (විනාඩි ${stay.durationMinutes})",
                                        fontSize = 12.sp,
                                        color = stayAccentColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                IconButton(onClick = { selectedOverlay = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                        }
                    }

                    is SelectedMapOverlay.EmergencyPin -> {
                        // Selecting an SOS pin also starts navigation, handled above.
                    }

                    null -> Unit
                }
            }
        }
    }
}
